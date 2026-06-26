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

  it('shows the correct total / cap per row', () => {
    render(<AlgorithmBalanceChart schedules={schedules} />);
    const rows = screen.getAllByTestId('algo-balance-row');
    expect(rows[0].textContent).toMatch(/3\s*ca\s*\/\s*giới hạn\s*5\s*\(\s*60\s*%\s*\)/); // BS.A: 3 total
    expect(rows[1].textContent).toMatch(/1\s*ca\s*\/\s*giới hạn\s*5\s*\(\s*20\s*%\s*\)/); // BS.B
  });

  it('uses custom caps when provided', () => {
    render(
      <AlgorithmBalanceChart
        schedules={schedules}
        staffCaps={{ 1: 3 }} // BS.A cap = 3
      />,
    );
    // BS.A: 3/3 = 100% → balanced or caution
    const rows = screen.getAllByTestId('algo-balance-row');
    expect(rows[0].textContent).toMatch(/3\s*\/\s*3/);
  });

  it('renders empty state when no schedules', () => {
    render(<AlgorithmBalanceChart schedules={[]} />);
    expect(screen.getByTestId('algo-balance-empty')).toBeInTheDocument();
  });

  it('respects the limit prop', () => {
    render(<AlgorithmBalanceChart schedules={schedules} limit={1} />);
    expect(screen.getAllByTestId('algo-balance-row')).toHaveLength(1);
  });

  it('marks overloaded staff (>1.5x cap) with a red badge', () => {
    const heavy = Array.from({ length: 10 }, (_, i) => ({
      scheduleId: i, staffId: 99, staffName: 'BS. X',
      workDate: `2026-06-${String(i + 1).padStart(2, '0')}`,
      shiftTypeId: 'L01', shiftTypeName: 'Trực 24/24',
    }));
    render(<AlgorithmBalanceChart schedules={heavy} staffCaps={{ 99: 6 }} />);
    const rows = screen.getAllByTestId('algo-balance-row');
    // Badge inside row has aria-label "Quá tải" and bg-error-container
    const badge = within(rows[0]).getByLabelText('Quá tải');
    expect(badge.className).toContain('bg-error-container');
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