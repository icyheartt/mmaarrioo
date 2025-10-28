package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;

public class GameCharacter {

    // === 상태(위치/속도/접지) ===
    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public boolean isGrounded = false;

    // === 이동/점프 파라미터 (GameWorld가 읽어서 사용) ===
    public float moveSpeed    = 300f;  // 지상에서 좌/우 목표 속도
    public float jumpVelocity = 650f;  // 지상 점프 초기속도
    public float swimThrust   = 420f;  // 수중 상승 가속(월드가 dt 곱해 적용)

    // === 입력 '의도' (GameWorld가 소비) ===
    // -1: left, 0: stop, 1: right
    private int desiredDir = 0;
    private boolean jumpRequested   = false;
    private boolean swimUpRequested = false;

    // === 렌더링 관련(월드가 애니메이션 상태만 결정) ===
    public enum Anim { IDLE, RUN, JUMP, SWIM }
    public Anim anim = Anim.IDLE;
    public boolean facingLeft = false;
    public final Sprite sprite;

    public GameCharacter(Vector2 startPos, Texture texture) {
        this.position.set(startPos);
        this.sprite = new Sprite(texture);
        syncSpriteToPosition();
    }

    // === 의도 입력 메서드 (이동만 담당) ===
    public void onPlayerLeft()     { desiredDir = -1; }
    public void onPlayerRight()    { desiredDir =  1; }
    public void onPlayerStop()     { desiredDir =  0; }
    public void onPlayerJump()     { jumpRequested = true; }
    public void onPlayerSwimUp()   { swimUpRequested = true; }

    // === 월드가 읽어가는 getter (consume 패턴) ===
    public int getDesiredDir() { return desiredDir; }

    public boolean consumeJumpRequested() {
        boolean j = jumpRequested;
        jumpRequested = false;
        return j;
    }

    public boolean consumeSwimUpRequested() {
        boolean s = swimUpRequested;
        swimUpRequested = false;
        return s;
    }

    // === 스프라이트 위치 동기화 ===
    public void syncSpriteToPosition() {
        sprite.setPosition(position.x, position.y);
    }

    // (선택) 사이즈 접근자 — 충돌계산에 자주 쓰이면 활용
    public float getWidth()  { return sprite.getWidth(); }
    public float getHeight() { return sprite.getHeight(); }
}
