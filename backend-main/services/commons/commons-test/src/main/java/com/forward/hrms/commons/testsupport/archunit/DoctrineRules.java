package com.forward.hrms.commons.testsupport.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * §5 doctrine enforcement rules. Each helper returns an {@link ArchRule}
 * that a per-service test class fires against its own {@link JavaClasses}.
 *
 * <h2>Why a helper, not a base class</h2>
 * ArchUnit's {@code @AnalyzeClasses} works best with a per-service entry
 * point that picks the package to scan. Keeping rules as static factory
 * methods (rather than a {@code ArchUnitTestBase} subclass) lets each
 * service compose just the rules it needs without inheritance ceremony.
 * Promotes to a shared base only if every service ends up running the
 * same five rules verbatim.
 *
 * <p>Rules ban behaviour, not files — they read the bytecode of the
 * scanned packages and report each violation with a
 * {@code SourceCodeLocation} that points to the offending line. PRs
 * that introduce a violation fail {@code mvn test}.
 */
public final class DoctrineRules {

    private DoctrineRules() {}

    /**
     * §5.1 — bans the scaleless {@code BigDecimal.divide(BigDecimal)}
     * overload outside {@code commons-core.money}. That overload throws
     * {@link ArithmeticException} on non-terminating decimals — silent
     * footgun. Routing through {@code commons-core.Money.divide /
     * .proration} forces CEILING at scale 2 per CLAUDE.md.
     *
     * <p>We also want a ban on
     * {@code divide(_, _, HALF_UP|HALF_EVEN|DOWN|FLOOR)}, but ArchUnit
     * reads bytecode and the literal {@link RoundingMode} arg isn't
     * recoverable from the call site without ASM-level inspection — every
     * 3-arg divide call looks the same regardless of which constant
     * was passed. The rounding-mode-value contract stays enforced by
     * the §2.1 sweep + code review; the scaleless-divide ban below
     * covers the unambiguous case automatically.
     */
    public static ArchRule moneyDivideMustGoThroughMoney() {
        return noClasses()
                .that().resideOutsideOfPackage("com.forward.hrms.commons.core.money..")
                .should(new ArchCondition<>("not call the scaleless BigDecimal.divide(BigDecimal) overload") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaClass cls, ConditionEvents events) {
                        for (JavaMethodCall call : cls.getMethodCallsFromSelf()) {
                            if (!BigDecimal.class.getName().equals(call.getTargetOwner().getFullName())) continue;
                            if (!"divide".equals(call.getName())) continue;
                            List<String> params = call.getTarget().getRawParameterTypes().stream()
                                    .map(p -> p.getFullName())
                                    .toList();
                            if (params.size() == 1 && BigDecimal.class.getName().equals(params.get(0))) {
                                events.add(SimpleConditionEvent.violated(call,
                                        "scaleless BigDecimal.divide(BigDecimal) at " + call.getSourceCodeLocation()
                                                + " — route through commons-core.Money.divide (CEILING at scale 2)"));
                            }
                        }
                    }
                })
                .because("§5.1 — money math goes through commons-core.Money; scaleless divide throws on non-terminating decimals");
    }

    /**
     * §5.2 — bans references to {@code java.time.ZoneId} /
     * {@code java.time.ZoneOffset} outside {@code commons-core}. Under
     * the trust-server doctrine (CLAUDE.md §0.5.B) every service runs
     * with TZ=Asia/Karachi, so any {@code ZoneId} math in service code
     * is wrong — {@code LocalDate.now()} / {@code LocalDateTime.now()}
     * (no-arg) are the right calls.
     */
    public static ArchRule noZoneIdReferencesOutsideCommons() {
        // Exclude all of commons.. — commons-saga.SagaService.health() uses
        // ZoneId.systemDefault() to render an Instant snapshot for actuator,
        // which is fine: foundational substrate, not service-side TZ math.
        return noClasses()
                .that().resideOutsideOfPackage("com.forward.hrms.commons..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.ZoneId")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.time.ZoneOffset")
                .because("§5.2 — services trust the JVM TZ (Asia/Karachi); no ZoneId math in service code");
    }

    /**
     * §5.3 — bans {@code new ObjectMapper()} (Jackson 2 + Jackson 3
     * variants). Forces injection of the project's Jackson bean
     * (commons-web auto-config wires it). Each service has its own
     * Jackson bean qualified by name; constructing one ad-hoc skips
     * the JavaTimeModule / JsonInclude / property-naming config.
     *
     * <p>Exception: {@code commons-*} modules. The autoconfig classes
     * legitimately construct mappers.
     */
    public static ArchRule noNewObjectMapperOutsideCommons() {
        DescribedPredicate<JavaConstructorCall> objectMapperCtor =
                new DescribedPredicate<>("constructor call to com.fasterxml.jackson.databind.ObjectMapper") {
                    @Override
                    public boolean test(JavaConstructorCall call) {
                        return "com.fasterxml.jackson.databind.ObjectMapper".equals(call.getTargetOwner().getFullName())
                                || "tools.jackson.databind.ObjectMapper".equals(call.getTargetOwner().getFullName());
                    }
                };
        return noClasses()
                .that().resideOutsideOfPackage("com.forward.hrms.commons..")
                .should(new ArchCondition<>("not call `new ObjectMapper()`") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaClass cls, ConditionEvents events) {
                        for (JavaConstructorCall call : cls.getConstructorCallsFromSelf()) {
                            if (objectMapperCtor.test(call)) {
                                events.add(SimpleConditionEvent.violated(call,
                                        "`new ObjectMapper()` at " + call.getSourceCodeLocation()
                                                + " — inject the bean instead (commons-web auto-config wires it)"));
                            }
                        }
                    }
                })
                .because("§5.3 — services share one ObjectMapper bean (JavaTimeModule + project conventions)");
    }

    /**
     * §5.4 — bans {@code Long}-typed money fields in {@code dto/} and
     * {@code entity/}. Money is {@code BigDecimal} per CLAUDE.md / §0.5.A.
     * Field names matched: {@code basic, amount, gross, net, deductions,
     * tax, eobi, pessi*, bonus, advance, salary, allowance, deduction,
     * encashment, gratuity, settlement}.
     *
     * <p>The list is intentionally conservative — it catches the
     * payroll surface area without flagging legitimate Long IDs. Add
     * more names here if a future field's name slips past it.
     */
    public static ArchRule noLongMoneyFieldsInDtoOrEntity() {
        // Money-shaped names. Negative lookahead `(?!.*Id$)` excludes FK
        // columns like `bonusEventId` / `advanceId` that share the prefix
        // but are entity ids, not money. Same goes for `*Code` / `*Count`.
        Pattern moneyName = Pattern.compile(
                "(?i)(?!.*(?:Id|Code|Count|Days|Hours|Minutes|At|By|Type|Status)$)" +
                "(basic|amount|gross|net|deductions?|tax|eobi|pessi\\w*|bonus\\w*|" +
                "advance\\w*|salary\\w*|allowance\\w*|encashment|gratuity|settlement\\w*|" +
                "fullBonus|installment)",
                Pattern.CASE_INSENSITIVE);
        Set<String> longTypes = Set.of(Long.class.getName(), "long");
        return fields()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..dto..", "..entity..")
                .and(new DescribedPredicate<JavaField>("have a money-shaped name") {
                    @Override
                    public boolean test(JavaField f) {
                        return moneyName.matcher(f.getName()).matches();
                    }
                })
                .should(new ArchCondition<>("be BigDecimal, not Long") {
                    @Override
                    public void check(JavaField field, ConditionEvents events) {
                        String t = field.getRawType().getFullName();
                        if (longTypes.contains(t)) {
                            events.add(SimpleConditionEvent.violated(field,
                                    "money-named field `" + field.getFullName() + "` is " + t
                                            + " — must be BigDecimal (CLAUDE.md §0.5.A)"));
                        }
                    }
                })
                .because("§5.4 — money is BigDecimal everywhere on the wire and in the schema");
    }
}
