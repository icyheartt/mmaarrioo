package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Block {
    public Sprite sprite;
    public Rectangle bound;
    private float xPos;
    private float yPos;
    private static int width;
    private static int height;

    public Block(float x, float y, Texture blockTexture) {
        xPos = x;
        yPos = y;
        width = 50;
        height = 50;
        this.sprite = new Sprite(blockTexture);
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

    public float getxPos() {
        return xPos;
    }

    public float getyPos() {
        return yPos;
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
