package com.hospital.scheduler.util;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensationDateCalculator - spec rules")
class CompensationDateCalculatorTest {

    @Mock
    private HolidayRepository holidayRepository;

    private CompensationDateCalculator calculator;

    @BeforeEach
    void setUp() {
        lenient().when(holidayRepository.findActiveHolidaysBetween(
                LocalDate.MIN, LocalDate.MAX))
                .thenReturn(Collections.emptyList());
        calculator = new CompensationDateCalculator(holidayRepository);
    }

    private static Holiday holiday(LocalDate date) {
        Holiday h = new Holiday();
        h.setHolidayDate(date);
        return h;
    }

    private void stubHolidays(LocalDate from, LocalDate to, List<Holiday> holidays) {
        when(holidayRepository.findActiveHolidaysBetween(from, to))
                .thenReturn(holidays);
    }

    static Stream<Arguments> mondayToThursdayDutySource() {
        return Stream.of(
                Arguments.of(LocalDate.of(2026, 6, 22), "Mon -> Tue"),
                Arguments.of(LocalDate.of(2026, 6, 23), "Tue -> Wed"),
                Arguments.of(LocalDate.of(2026, 6, 24), "Wed -> Thu"),
                Arguments.of(LocalDate.of(2026, 6, 25), "Thu -> Fri")
        );
    }

    // --- Base rules (no holidays) ---

    @Nested
    @DisplayName("Base compensation day (no holidays)")
    class BaseCompensationDay {

        @ParameterizedTest(name = "{1}")
        @MethodSource("com.hospital.scheduler.util.CompensationDateCalculatorTest#mondayToThursdayDutySource")
        @DisplayName("Mon-Thu duty -> next calendar day")
        void mondayToThursday(LocalDate shift, String label) {
            LocalDate expected = shift.plusDays(1);
            assertThat(calculator.calculateWithoutHolidays(shift)).isEqualTo(expected);
            assertThat(calculator.calculate(shift)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Sunday duty -> Monday (next day)")
        void sundayDuty() {
            LocalDate sunday = LocalDate.of(2026, 6, 21);
            assertThat(calculator.calculateWithoutHolidays(sunday))
                    .isEqualTo(LocalDate.of(2026, 6, 22));
        }

        @Test
        @DisplayName("Friday duty -> Tuesday (Tue of the following calendar week)")
        void fridayDuty() {
            // Jun 2026: 1=Mon, 5=Fri, 6=Sat, 7=Sun, 8=Mon, 9=Tue
            // findNextDayOfWeek(Sat, TUESDAY): Sat->Sun->Mon(skip)->Tue Jun 9
            LocalDate friday = LocalDate.of(2026, 6, 5);
            assertThat(calculator.calculateWithoutHolidays(friday))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
            assertThat(calculator.calculate(friday))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
        }

        @Test
        @DisplayName("Saturday duty -> Tuesday")
        void saturdayDuty() {
            // findNextDayOfWeek(Sun, TUESDAY): Sun->Mon->Tue Jun 9
            LocalDate saturday = LocalDate.of(2026, 6, 6);
            assertThat(calculator.calculateWithoutHolidays(saturday))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
            assertThat(calculator.calculate(saturday))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
        }
    }

    // --- Holiday avoidance ---

    @Nested
    @DisplayName("Holiday avoidance")
    class HolidayAvoidance {

        @Test
        @DisplayName("Mon-Thu: next day is holiday -> advance to next non-holiday weekday")
        void skipHolidayMonToThu() {
            LocalDate comp = LocalDate.of(2026, 6, 23);
            stubHolidays(comp, comp.plusYears(1), List.of(holiday(comp)));
            // Mon Jun 22 -> normally Tue Jun 23 -> holiday -> advance -> Wed Jun 24
            assertThat(calculator.calculate(LocalDate.of(2026, 6, 22)))
                    .isEqualTo(LocalDate.of(2026, 6, 24));
        }

        @Test
        @DisplayName("Mon-Thu: Tue-Fri all holidays -> advance to following Monday")
        void skipMultipleHolidays() {
            LocalDate from = LocalDate.of(2026, 6, 23);
            List<Holiday> h = List.of(
                    holiday(LocalDate.of(2026, 6, 23)),
                    holiday(LocalDate.of(2026, 6, 24)),
                    holiday(LocalDate.of(2026, 6, 25))
            );
            stubHolidays(from, from.plusYears(1), h);
            // Mon Jun 22 -> Tue/Wed/Thu all holiday -> advance -> Fri Jun 26
            assertThat(calculator.calculate(LocalDate.of(2026, 6, 22)))
                    .isEqualTo(LocalDate.of(2026, 6, 26));
        }

        @Test
        @DisplayName("Fri duty: compensation lands on holiday -> skip Mon+Fri (per spec)")
        void skipMonAndFriForFriDuty() {
            LocalDate comp = LocalDate.of(2026, 6, 9);
            stubHolidays(comp, comp.plusYears(1), List.of(holiday(comp)));
            // Fri Jun 5 -> Tue Jun 9 holiday -> advance: Mon(skip) Fri(skip) -> Wed Jun 10
            assertThat(calculator.calculate(LocalDate.of(2026, 6, 5)))
                    .isEqualTo(LocalDate.of(2026, 6, 10));
        }

        @Test
        @DisplayName("Sat duty: compensation lands on holiday -> skip Mon+Fri (per spec)")
        void skipMonAndFriForSatDuty() {
            LocalDate comp = LocalDate.of(2026, 6, 9);
            stubHolidays(comp, comp.plusYears(1), List.of(holiday(comp)));
            // Sat Jun 6 -> Tue Jun 9 holiday -> advance: Mon(skip) Fri(skip) -> Wed Jun 10
            assertThat(calculator.calculate(LocalDate.of(2026, 6, 6)))
                    .isEqualTo(LocalDate.of(2026, 6, 10));
        }

        @Test
        @DisplayName("Sun duty: Mon compensation day is holiday -> skip to Tuesday")
        void skipHolidayAfterSunday() {
            LocalDate comp = LocalDate.of(2026, 6, 22);
            stubHolidays(comp, comp.plusYears(1), List.of(holiday(comp)));
            // Sun Jun 21 -> Mon Jun 22 holiday -> advance -> Tue Jun 23
            assertThat(calculator.calculate(LocalDate.of(2026, 6, 21)))
                    .isEqualTo(LocalDate.of(2026, 6, 23));
        }
    }

    // --- Edge cases ---

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("calculateWithoutHolidays: bypasses DB")
        void pureDateArithmetic() {
            assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 5)))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
            assertThat(calculator.calculateWithoutHolidays(LocalDate.of(2026, 6, 6)))
                    .isEqualTo(LocalDate.of(2026, 6, 9));
        }
    }
}
