// File: Pipe.java
package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

public class Pipe {
    public enum Orientation { DOWN, UP }

    private final Sprite sprite;
    private final Rectangle bounds;
    private final Orientation orientation;

    public Pipe(float x, float y, Texture pipeTexture, Orientation orientation) {
        this.sprite = new Sprite(pipeTexture);
        this.sprite.setPosition(x, y);
        this.bounds = new Rectangle(x, y, sprite.getWidth(), sprite.getHeight());
        this.orientation = orientation;
    }

    public void draw(SpriteBatch batch) { sprite.draw(batch); }

    public Rectangle getBounds() {
        bounds.setPosition(sprite.getX(), sprite.getY());
        bounds.setSize(sprite.getWidth(), sprite.getHeight());
        return bounds;
    }

    public float getX() { return sprite.getX(); }
    public float getY() { return sprite.getY(); }

    public Orientation getOrientation() { return orientation; }
}
