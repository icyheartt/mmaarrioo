package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class CoinObject {

    public static int CoinWidth = 90;
    public static int CoinHeight = 72;

    public Vector2 position;
    public Vector2 velocity;
    public Sprite sprite;

    // 충돌 판정을 위한 사각 영역
    public Rectangle bounds;

    /**
     * 떨어지는 오브젝트 생성자
     * @param region 오브젝트 텍스처
     * @param startX 시작 X 위치
     * @param startY 시작 Y 위치
     * @param speed 떨어지는 속도
     */
    public CoinObject(Texture region, float startX, float startY, float speed) {
        this.position = new Vector2(startX, startY);
        this.velocity = new Vector2(0, speed); // 아래로만 떨어지므로 Y 속도만 가짐

        this.sprite = new Sprite(region);
        this.sprite.setPosition(position.x, position.y);
        this.sprite.setSize(CoinWidth, CoinHeight);

        // 충돌 영역 초기화
        this.bounds = new Rectangle(position.x, position.y, sprite.getWidth(), sprite.getHeight());
    }

    /**
     * 매 프레임 업데이트
     */
    public void update(float delta) {
        // 위치 업데이트
        position.y += velocity.y * delta;

        // 스프라이트와 충돌 영역 위치 동기화
        sprite.setPosition(position.x, position.y);
        bounds.setPosition(position.x, position.y);
    }

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }
}
