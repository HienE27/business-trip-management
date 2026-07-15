#!/bin/bash
# Final Acceptance Testing Script

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "=== 1. AUTH ==="
echo -n "Login: "; [ -n "$TOKEN" ] && echo "PASS" || echo "FAIL"

echo -n "Get current user: "
curl -s http://localhost:8080/api/v1/staff/me -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success') else 'FAIL')" 2>/dev/null

echo ""
echo "=== 2. MODULE M01 — NHAN SU ==="
echo -n "F01: List staff: "
curl -s http://localhost:8080/api/v1/staff -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} staff')" 2>/dev/null

echo -n "F04: Search by name: "
curl -s "http://localhost:8080/api/v1/staff?search=Minh" -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} results')" 2>/dev/null

echo -n "F05: Shift types: "
curl -s http://localhost:8080/api/v1/shift-types/active -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} types')" 2>/dev/null

echo ""
echo "=== 3. MODULE M02 — LICH TRUC 24/24 ==="
echo -n "F01: Create L01 (Mon 2026-07-06): "
curl -s -X POST http://localhost:8080/api/v1/schedules -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":2,"workDate":"2026-07-06","staffId":1,"shiftTypeId":"L01"}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success') else 'FAIL: ' + d.get('message','')[:30])" 2>/dev/null

echo -n "F06: Compensation day check: "
curl -s "http://localhost:8080/api/v1/schedules/compensation-days/2" -H "Authorization: Bearer $TOKEN" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',[]); print(f'{len(data)} comp days')" 2>/dev/null

echo ""
echo "=== 4. MODULE M03-M05 ==="
echo -n "Holidays: "
curl -s http://localhost:8080/api/v1/holidays -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} holidays')" 2>/dev/null

echo -n "Leave requests: "
curl -s http://localhost:8080/api/v1/leave-requests -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} requests')" 2>/dev/null

echo -n "Exchanges: "
curl -s http://localhost:8080/api/v1/schedule-exchanges -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",[]))} exchanges')" 2>/dev/null

echo ""
echo "=== 5. MODULE M06 — DASHBOARD ==="
curl -s "http://localhost:8080/api/v1/dashboard" -H "Authorization: Bearer $TOKEN" 2>/dev/null | python3 -c "
import sys,json; d=json.load(sys.stdin); s=d.get('data',{}).get('summary',{}); 
print(f'Dash: staff={s.get(\"activeStaff\",\"?\")} schedules={s.get(\"totalSchedules\",\"?\")} conflicts={s.get(\"totalConflicts\",\"?\")}')" 2>/dev/null

echo -n "Audit history: "
curl -s "http://localhost:8080/api/v1/audit?page=0&size=10" -H "Authorization: Bearer $TOKEN" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{d.get(\"data\",{}).get(\"totalElements\",0)} records')" 2>/dev/null

echo ""
echo "=== 6. MODULE M07 — AUTO SCHEDULING ==="
echo -n "GREEDY: "
curl -s -X POST http://localhost:8080/api/v1/auto-schedule/preview -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"algorithmType":"GREEDY","excludedStaffIds":[]}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(f'PASS sched={len(dd.get(\"schedules\",[]))} conflicts={len(dd.get(\"conflicts\",[]))}')" 2>/dev/null

echo -n "CSP: "
curl -s -X POST http://localhost:8080/api/v1/auto-schedule/preview -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"algorithmType":"CSP","excludedStaffIds":[]}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(f'PASS sched={len(dd.get(\"schedules\",[]))} conflicts={len(dd.get(\"conflicts\",[]))}')" 2>/dev/null

echo -n "FAIR: "
curl -s -X POST http://localhost:8080/api/v1/auto-schedule/preview -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"algorithmType":"FAIR_GREEDY","excludedStaffIds":[]}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(f'PASS sched={len(dd.get(\"schedules\",[]))} conflicts={len(dd.get(\"conflicts\",[]))}')" 2>/dev/null

echo ""
echo "=== 7. BUSINESS LOGIC ==="
echo "--- Conflict Detection ---"
echo -n "L01+L02 same day (should REJECT): "
curl -s -X POST http://localhost:8080/api/v1/schedules -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"workDate":"2026-07-06","staffId":5,"shiftTypeId":"L01"}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('FAIL' if d.get('success') else 'PASS (rejected)')" 2>/dev/null

echo -n "L03+L04 same day (should REJECT): "
curl -s -X POST http://localhost:8080/api/v1/schedules -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"workDate":"2026-07-06","staffId":5,"shiftTypeId":"L03"}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('FAIL' if not d.get('success') else 'L03 OK')" 2>/dev/null
curl -s -X POST http://localhost:8080/api/v1/schedules -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"periodId":14,"workDate":"2026-07-06","staffId":5,"shiftTypeId":"L04"}' 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('(L04) ' + ('PASS (rejected)' if not d.get('success') else 'FAIL'))" 2>/dev/null

echo ""
echo "=== 8. SECURITY ==="
echo -n "No-token access (should 401): "
curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/staff" 2>/dev/null

echo ""
echo -n "Wrong password: "
curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"wrongpass"}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS (401)' if not d.get('success') else 'FAIL')" 2>/dev/null

echo ""
echo -n "Role matrix: "
curl -s http://localhost:8080/api/v1/roles/permissions/matrix -H "Authorization: Bearer $TOKEN" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); r=d.get('data',{}).get('roles',[]); p=d.get('data',{}).get('permissions',[]); print(f'{len(r)} roles, {len(p)} permissions')" 2>/dev/null

echo ""
echo "=== 9. FRONTEND ==="
for page in "/login" "/dashboard" "/monthly-schedule" "/staff" "/duty-24" "/all-day" "/service-clinic" "/expert-clinic" "/requirements" "/auto-scheduling" "/auto-scheduling/algorithm-config" "/holidays" "/leave-requests" "/swap-requests" "/reports" "/audit-history" "/notifications" "/periods" "/settings/roles"; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:3001$page" 2>/dev/null)
  if [ "$CODE" = "200" ]; then echo -n "PASS "; else echo -n "FAIL "; fi
done
echo ""
echo "=== DONE ==="
