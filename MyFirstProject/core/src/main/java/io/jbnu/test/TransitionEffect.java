package io.jbnu.test;

public class TransitionEffect {
    public enum Phase { IDLE, FADE_OUT, LOADING, FADE_IN }

    private Phase phase = Phase.IDLE;
    private float t = 0f;
    private final float fadeOutDur;
    private final float fadeInDur;
    private Runnable onMidpoint; // 레벨 스왑 등

    public TransitionEffect(float fadeOutDur, float fadeInDur) {
        this.fadeOutDur = fadeOutDur;
        this.fadeInDur = fadeInDur;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public void start(Runnable onMidpoint) {
        this.onMidpoint = onMidpoint;
        this.t = 0f;
        this.phase = Phase.FADE_OUT;
    }

    /** 0.0 ~ 1.0 (현재 페이드 알파) */
    public float getAlpha() {
        switch (phase) {
            case IDLE: return 0f;
            case FADE_OUT: return clamp01(t / fadeOutDur);
            case LOADING: return 1f;
            case FADE_IN: return 1f - clamp01(t / fadeInDur);
            default: return 0f;
        }
    }

    public void update(float dt) {
        if (phase == Phase.IDLE) return;

        t += dt;

        if (phase == Phase.FADE_OUT) {
            if (t >= fadeOutDur) {
                // 중앙 지점: 실제 레벨 스왑 실행
                if (onMidpoint != null) onMidpoint.run();
                t = 0f;
                phase = Phase.LOADING; // 한 틱 유지
            }
        } else if (phase == Phase.LOADING) {
            // 다음 프레임부터 페이드 인
            t = 0f;
            phase = Phase.FADE_IN;
        } else if (phase == Phase.FADE_IN) {
            if (t >= fadeInDur) {
                phase = Phase.IDLE;
                t = 0f;
            }
        }
    }

    private float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
