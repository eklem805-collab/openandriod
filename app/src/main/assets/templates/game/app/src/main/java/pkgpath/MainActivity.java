package __PKG__;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Космический уклонист — пиксельная аркада.
 * Управление: держи палец на экране — корабль летит за ним.
 * Уворачивайся от астероидов (#), набирай очки.
 */
public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }

    @Override
    protected void onPause() {
        gameView.pause();
        super.onPause();
    }

    /** Игровое поле с собственным потоком-циклом. */
    static class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

        private Thread gameThread;
        private volatile boolean running;
        private volatile boolean gameOver;

        // Корабль игрока (пиксельный треугольник)
        private float shipX, shipY;
        private float targetX, targetY;
        private final int shipSize = 40;

        // Астероиды
        private static final int MAX_ROCKS = 8;
        private final float[] rockX = new float[MAX_ROCKS];
        private final float[] rockY = new float[MAX_ROCKS];
        private final float[] rockSpeed = new float[MAX_ROCKS];
        private final int[] rockSize = new int[MAX_ROCKS];

        // Звёзды на фоне
        private static final int MAX_STARS = 40;
        private final float[] starX = new float[MAX_STARS];
        private final float[] starY = new float[MAX_STARS];
        private final float[] starSpeed = new float[MAX_STARS];

        private int score;
        private int bestScore;
        private int w, h;
        private long lastTick;
        private final Paint paint = new Paint();
        private final Paint paintBig = new Paint();

        GameView(Context context) {
            super(context);
            getHolder().addCallback(this);
            setFocusable(true);
            paintBig.setTextSize(48);
            paintBig.setFakeBoldText(true);
        }

        private void reset() {
            score = 0;
            gameOver = false;
            for (int i = 0; i < MAX_ROCKS; i++) spawnRock(i, true);
            for (int i = 0; i < MAX_STARS; i++) {
                starX[i] = (float) Math.random() * w;
                starY[i] = (float) Math.random() * h;
                starSpeed[i] = 2f + (float) Math.random() * 4f;
            }
        }

        private void spawnRock(int i, boolean anywhere) {
            rockX[i] = 20 + (float) Math.random() * Math.max(1, w - 40);
            rockY[i] = anywhere ? (float) Math.random() * h : -40;
            rockSpeed[i] = 4f + (float) Math.random() * 6f + Math.min(score / 200f, 8f);
            rockSize[i] = 22 + (int) (Math.random() * 26);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            w = getWidth();
            h = getHeight();
            if (shipX == 0) {
                shipX = w / 2f;
                shipY = h * 0.8f;
                targetX = shipX;
                targetY = shipY;
            }
            reset();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            w = width;
            h = height;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public void resume() {
            running = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        public void pause() {
            running = false;
            try {
                if (gameThread != null) gameThread.join(500);
            } catch (InterruptedException ignored) {
            }
        }

        public void run() {
            lastTick = System.currentTimeMillis();
            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastTick < 16) {
                    try {
                        Thread.sleep(16 - (now - lastTick));
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                float dt = (now - lastTick) / 16f;
                lastTick = now;
                if (!gameOver) update(dt);
                draw();
            }
        }

        private void update(float dt) {
            // Плавное движение корабля к пальцу
            shipX += (targetX - shipX) * 0.25f * dt;
            shipY += (targetY - shipY) * 0.25f * dt;
            if (shipY < 60) shipY = 60;
            if (shipY > h - 40) shipY = h - 40;

            score += dt;

            for (int i = 0; i < MAX_ROCKS; i++) {
                rockY[i] += rockSpeed[i] * dt;
                if (rockY[i] > h + 40) {
                    spawnRock(i, false);
                }
                // Столкновение (примерно, по квадратам)
                float dx = Math.abs(rockX[i] - shipX);
                float dy = Math.abs(rockY[i] - shipY);
                if (dx < (rockSize[i] + shipSize) / 2f && dy < (rockSize[i] + shipSize) / 2f) {
                    gameOver = true;
                    if (score > bestScore) bestScore = score;
                }
            }
            for (int i = 0; i < MAX_STARS; i++) {
                starY[i] += starSpeed[i] * dt;
                if (starY[i] > h) {
                    starY[i] = 0;
                    starX[i] = (float) Math.random() * w;
                }
            }
        }

        private void draw() {
            SurfaceHolder holder = getHolder();
            Canvas c = holder.lockCanvas();
            if (c == null) return;
            try {
                c.drawColor(Color.rgb(12, 5, 25));

                // Звёзды
                paint.setColor(Color.rgb(160, 130, 220));
                for (int i = 0; i < MAX_STARS; i++) {
                    c.drawRect(starX[i], starY[i], starX[i] + 3, starY[i] + 3, paint);
                }

                // Астероиды — фиолетовые пиксель-квадраты
                paint.setColor(Color.rgb(192, 132, 252));
                for (int i = 0; i < MAX_ROCKS; i++) {
                    c.drawRect(rockX[i] - rockSize[i] / 2f, rockY[i] - rockSize[i] / 2f,
                            rockX[i] + rockSize[i] / 2f, rockY[i] + rockSize[i] / 2f, paint);
                }

                // Корабль — яркий треугольник из линий
                paint.setColor(Color.rgb(74, 222, 128));
                c.drawRect(shipX - shipSize / 2f, shipY, shipX + shipSize / 2f, shipY + shipSize, paint);
                paint.setColor(Color.rgb(233, 213, 255));
                c.drawRect(shipX - 6, shipY - 14, shipX + 6, shipY, paint);

                // Счёт
                paintBig.setColor(Color.rgb(237, 233, 254));
                c.drawText("СЧЁТ: " + (int) score, 20, 60, paintBig);
                paintBig.setColor(Color.rgb(139, 123, 184));
                c.drawText("РЕКОРД: " + (int) bestScore, 20, 110, paintBig);

                if (gameOver) {
                    paintBig.setColor(Color.rgb(244, 114, 182));
                    c.drawText("СТОЛКНОВЕНИЕ!", w / 2f - 160, h / 2f, paintBig);
                    paintBig.setColor(Color.rgb(237, 233, 254));
                    c.drawText("коснись для рестарта", w / 2f - 170, h / 2f + 60, paintBig);
                }
            } finally {
                holder.unlockCanvasAndPost(c);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && gameOver) {
                reset();
                return true;
            }
            targetX = event.getX();
            targetY = event.getY();
            return true;
        }
    }
}
