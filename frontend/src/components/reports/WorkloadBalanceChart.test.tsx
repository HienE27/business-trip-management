import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WorkloadBalanceChart } from './WorkloadBalanceChart';

const data = [
  { staffId: 1, staffName: 'BS. A', L01: 5, L02: 0, L03: 0, L04: 0, total: 5, maxShiftsPerMonth: 6 },
  { staffId: 2, staffName: 'BS. B', L01: 2, L02: 1, L03: 0, L04: 0, total: 3, maxShiftsPerMonth: 6 },
  { staffId: 3, staffName: 'BS. C', L01: 0, L02: 0, L03: 4, L04: 0, total: 4, maxShiftsPerMonth: 8 },
];

describe('WorkloadBalanceChart', () => {
  it('renders one row per staff', () => {
    render(<WorkloadBalanceChart data={data} view="ALL" />);
    expect(screen.getAllByTestId('balance-row')).toHaveLength(3);
  });

  it('orders rows by descending count for the active view', () => {
    render(<WorkloadBalanceChart data={data} view="L01" />);
    const values = screen.getAllByTestId('balance-value').map((n) => n.textContent ?? '');
    // L01: A=5, B=2, C=0 -> sorted A, B, C
    expect(values[0]).toMatch(/5/);
    expect(values[1]).toMatch(/2/);
    expect(values[2]).toMatch(/0/);
  });

  it('uses the per-view cap in the legend', () => {
    render(<WorkloadBalanceChart data={data} view="L01" />);
    // L01 cap = ceil(6*0.6)=4, so /4 in the value column.
    expect(screen.getAllByTestId('balance-value')[0].textContent).toMatch(/\/\s*4/);
  });

  it('renders the empty state when no rows are passed', () => {
    render(<WorkloadBalanceChart data={[]} view="ALL" />);
    expect(screen.getByTestId('balance-chart-empty')).toBeInTheDocument();
  });

  it('respects the limit prop', () => {
    render(<WorkloadBalanceChart data={data} view="ALL" limit={2} />);
    expect(screen.getAllByTestId('balance-row')).toHaveLength(2);
  });

  it('emits an accessible label', () => {
    render(<WorkloadBalanceChart data={data} view="L03" />);
    expect(
      screen.getByRole('img', { name: /phòng khám dịch vụ/i }),
    ).toBeInTheDocument();
  });
});