import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { AlgorithmBalanceChart } from './AlgorithmBalanceChart';

const schedules = [
  { scheduleId: 1, staffId: 1, staffName: 'BS. A', workDate: '2026-06-01', shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24' },
  { scheduleId: 2, staffId: 1, staffName: 'BS. A', workDate: '2026-06-03', shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24' },
  { scheduleId: 3, staffId: 1, staffName: 'BS. A', workDate: '2026-06-05', shiftTypeId: 'L02', shiftTypeName: 'Thông tầm' },
  { scheduleId: 4, staffId: 2, staffName: 'BS. B', workDate: '2026-06-02', shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24' },
  { scheduleId: 5, staffId: 3, staffName: 'BS. C', workDate: '2026-06-01', shiftTypeId: 'L03', shiftTypeName: 'PK Dịch vụ' },
];

describe('AlgorithmBalanceChart', () => {
  it('renders one row per staff', () => {
    render(<AlgorithmBalanceChart schedules={schedules} />);
    expect(screen.getAllByTestId('algo-balance-row')).toHaveLength(3);
  });

  it('orders rows by descending ratio (most loaded first)', () => {
    render(<AlgorithmBalanceChart schedules={schedules} />);
    const values = screen.getAllByTestId('algo-balance-row').map(
      (row) => row.textContent ?? '',
    );
    // BS.A has 3 (highest), BS.B has 1, BS.C has 1
    expect(values[0]).toContain('BS. A');
    expect(values[1]).toContain('BS. B');
    expect(values[2]).toContain('BS. C');
  });

  it('shows the correct total / avg per row', () => {
    render(<AlgorithmBalanceChart schedules={schedules} />);
    const rows = screen.getAllByTestId('algo-balance-row');
    // 5 total shifts / 3 staff = 1.67 avg. BS.A has 3 (180%), BS.B has 1 (60%), BS.C has 1 (60%)
    expect(rows[0].textContent).toMatch(/3\s*ca\s*\/\s*TB\s*1\.7/); // BS.A: 3 / 1.67
    expect(rows[1].textContent).toMatch(/1\s*ca\s*\/\s*TB\s*1\.7/); // BS.B
  });

  it('classifies overload vs balanced based on average', () => {
    // 5 shifts for 1 staff vs 3 others with 1 each: avg = 2, ratio = 2.5 → overloaded
    const heavy = Array.from({ length: 5 }, (_, i) => ({
      scheduleId: i, staffId: 99, staffName: 'BS. Heavy',
      workDate: `2026-06-${String(i + 1).padStart(2, '0')}`,
      shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24',
    }));
    const others = Array.from({ length: 3 }, (_, i) => ({
      scheduleId: 100 + i, staffId: 100 + i, staffName: `BS. Light${i}`,
      workDate: '2026-06-01',
      shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24',
    }));
    render(<AlgorithmBalanceChart schedules={[...heavy, ...others]} />);
    const rows = screen.getAllByTestId('algo-balance-row');
    // BS.Heavy has 5 (avg=2, ratio=2.5 ≥ 1.5 → overloaded)
    const badge = within(rows[0]).getByLabelText('Quá tải');
    expect(badge).toBeInTheDocument();
  });

  it('renders empty state when no schedules', () => {
    render(<AlgorithmBalanceChart schedules={[]} />);
    expect(screen.getByTestId('algo-balance-empty')).toBeInTheDocument();
  });

  it('respects the limit prop', () => {
    render(<AlgorithmBalanceChart schedules={schedules} limit={1} />);
    expect(screen.getAllByTestId('algo-balance-row')).toHaveLength(1);
  });

  it('has a descriptive aria-label on the root element', () => {
    render(
      <AlgorithmBalanceChart
        schedules={schedules}
        title="Cân bằng tải"
      />,
    );
    expect(
      screen.getByRole('img', { name: 'Cân bằng tải' }),
    ).toBeInTheDocument();
  });
});