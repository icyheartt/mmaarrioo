package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

public class Flag {
    public Sprite sprite;
    private float xPos;
    private float yPos;
    private static int width;
    private static int height;
    public Rectangle bound;

    public Flag(float x, float y, Texture flagTexture) {
        xPos = x;
        yPos = y;
        width = 50;
        height = 50;
        this.sprite = new Sprite(flagTexture);
        this.sprite.setPosition(xPos, yPos);
        this.sprite.setSize(width, height);

        bound = new Rectangle();
    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }

    public Rectangle getBound(){
        bound.x = xPos;
        bound.y = yPos;
        bound.width = width;
        bound.height = height;
        return bound;
    }
}
