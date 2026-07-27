package com.kma.knowledge.rag.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerCorrectnessJudgeTest {
    private final DeterministicAnswerCorrectnessJudge judge = new DeterministicAnswerCorrectnessJudge();

    @Test
    void exactNormalizedAnswerPassesAndUnrelatedAnswerFails() {
        assertThat(judge.judge("党员每月缴纳党费。", "党员每月缴纳党费").score()).isEqualTo(1.0);
        assertThat(judge.judge("实现共产主义。", "根据参考资料[1]，党的最终目标是实现共产主义。").score())
            .isEqualTo(1.0);
        assertThat(judge.judge(
            "党员个人服从党的组织，少数服从多数，下级组织服从上级组织，各级组织和全体党员服从党的全国代表大会和中央委员会。",
            "四个服从包括党员个人服从党的组织。").score()).isLessThan(0.7);
        assertThat(judge.judge("党员每月缴纳党费", "天气晴朗适合出行").score()).isZero();
    }
}
