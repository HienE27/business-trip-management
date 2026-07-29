package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CspSearchEngineTest {

    @Test
    void emptyDomainIsDeadEndNotCompleteSolution() {
        CompensationDateCalculator calculator =
                new CompensationDateCalculator(mock(HolidayRepository.class));
        CspSearchEngine engine = new CspSearchEngine(calculator, new CspNogoodStore());

        ProblemData data = ProblemData.builder()
                .numDays(1)
                .numShifts(1)
                .numStaff(1)
                .numVars(1)
                .varDay(new int[]{0})
                .varShift(new int[]{0})
                .varSlot(new int[]{0})
                .varSpecialty(new int[]{0})
                .slotCount(new int[][]{{1}})
                .leaveMatrix(new boolean[][]{{true}})
                .holidayDays(new boolean[]{false})
                .staffMaxShifts(new int[]{1})
                .domains(new BitSet[]{new BitSet(1)})
                .constraintGraph(singleEmptyAdjacency())
                .baseDate(LocalDate.of(2026, 8, 1))
                .minShiftsPerWeekByShift(new int[]{0})
                .dayToWeek(new int[]{0})
                .adjacentL01Pairs(new int[0])
                .adjacentL01PairCount(0)
                .shiftTypeIds(new String[]{"L01"})
                .varsByDay(singleVarByDay())
                .compDayIdx(new int[]{-1})
                .build();

        CspSearchEngine.Result result = engine.solve(data, System.currentTimeMillis());

        assertThat(result.isValid()).isFalse();
        assertThat(result.isPartial()).isFalse();
        assertThat(result.getAssignment()).isNullOrEmpty();
        assertThat(result.getErrors()).contains("Không tìm được lịch hợp lệ");
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Integer>[] singleEmptyAdjacency() {
        return new java.util.List[]{Collections.emptyList()};
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Integer>[] singleVarByDay() {
        return new java.util.List[]{Collections.singletonList(0)};
    }
}
