package ru.survey.service.rules;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/** Доля показа: случайное попадание при каждом событии (docs/survey-service.md, п. 6.2). */
@Component
public class Sampler {

    public boolean isSampled(int showPercent) {
        if (showPercent >= 100) {
            return true;
        }
        if (showPercent <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < showPercent;
    }
}
