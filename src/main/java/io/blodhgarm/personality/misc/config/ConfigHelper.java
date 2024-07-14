package io.blodhgarm.personality.misc.config;

import io.blodhgarm.personality.api.character.Character;
import io.blodhgarm.personality.misc.config.PersonalityConfigModel.GradualValue;

import static io.blodhgarm.personality.PersonalityMod.CONFIG;
import static java.lang.Math.*;

public class ConfigHelper {

    public static float apply(GradualValue c, Character character) {
        double a = calculateCurveModifier(c);
        double x = character.getPreciseAge() - c.minAge;

        return (float) switch (c.calculationCurve) {
            case NONE -> 0;
            case LINEAR -> a*x;
            case QUADRATIC -> a*pow(x,2);
            case SQRT -> a*sqrt(x);
            case EXPONENTIAL -> pow(E, a*x - 1);
            case LOGARITHMIC -> a*log(x) + 1;
            case EXPONENTIAL_EXTREME -> (pow(E,x) - 1)/a;
            case LOGARITHMIC_EXTREME -> log(a*x+1);
        } + c.startingValue;
    }

    public static float apply(io.blodhgarm.personality.misc.config.PersonalityConfig.GradualValue c, Character character) {
        double a = calculateCurveModifier(c);
        double x = character.getPreciseAge() - c.minAge();

        return (float) switch (c.calculationCurve()) {
            case NONE -> 0;
            case LINEAR -> a*x;
            case QUADRATIC -> a*pow(x,2);
            case SQRT -> a*sqrt(x);
            case EXPONENTIAL -> pow(E, a*x - 1);
            case LOGARITHMIC -> a*log(x) + 1;
            case EXPONENTIAL_EXTREME -> (pow(E,x) - 1)/a;
            case LOGARITHMIC_EXTREME -> log(a*x+1);
        } + c.startingValue();
    }

    public static boolean shouldApply(GradualValue config, Character character) {
        if (character == null) return false;

        int age = character.getAge();

        return age >= config.minAge && age <= maxAge(config);
    }

    public static boolean shouldApply(io.blodhgarm.personality.misc.config.PersonalityConfig.GradualValue config, Character character) {
        if (character == null) return false;

        int age = character.getAge();

        return age >= config.minAge() && age <= maxAge(config);
    }

    private static int maxAge(GradualValue c) {
        return (c.maxAge == Integer.MAX_VALUE)
            ? CONFIG.defaultMaximumAge() + CONFIG.defaultMaximumAge()
            : c.maxAge;
    }

    private static int maxAge(io.blodhgarm.personality.misc.config.PersonalityConfig.GradualValue c) {
        return (c.maxAge() == Integer.MAX_VALUE)
                ? CONFIG.defaultMaximumAge() + CONFIG.defaultMaximumAge()
                : c.maxAge();
    }

    public static double calculateCurveModifier(GradualValue c) {
        double ageRange = c.maxAge - c.minAge;
        double valueRange = c.endingValue - c.startingValue;

        return switch (c.calculationCurve) {
            case NONE -> 0;
            case LINEAR -> valueRange / ageRange;
            case QUADRATIC -> valueRange / pow(ageRange, 2);
            case SQRT -> valueRange / sqrt(ageRange);
            case EXPONENTIAL -> log( valueRange + 1 ) / ageRange;
            case LOGARITHMIC -> valueRange / log(ageRange + 1);
            case EXPONENTIAL_EXTREME -> (pow(E, ageRange) - 1)/ valueRange;
            case LOGARITHMIC_EXTREME -> (pow(E, valueRange) - 1) / ageRange;
        };
    }

    public static double calculateCurveModifier(io.blodhgarm.personality.misc.config.PersonalityConfig.GradualValue c) {
        double ageRange = c.maxAge() - c.minAge();
        double valueRange = c.endingValue() - c.startingValue();

        return switch (c.calculationCurve()) {
            case NONE -> 0;
            case LINEAR -> valueRange / ageRange;
            case QUADRATIC -> valueRange / pow(ageRange, 2);
            case SQRT -> valueRange / sqrt(ageRange);
            case EXPONENTIAL -> log( valueRange + 1 ) / ageRange;
            case LOGARITHMIC -> valueRange / log(ageRange + 1);
            case EXPONENTIAL_EXTREME -> (pow(E, ageRange) - 1)/ valueRange;
            case LOGARITHMIC_EXTREME -> (pow(E, valueRange) - 1) / ageRange;
        };
    }
}
