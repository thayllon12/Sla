package com.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread gameThread;
    private volatile boolean running = false;
    private SurfaceHolder holder;
    private Paint paint, hudPaint, textPaint, panelPaint;
    private int screenW, screenH;
    private Random random = new Random();

    // Game state
    private enum State { MENU, PLAYING, GAMEOVER }
    private State state = State.MENU;
    private int score = 0;
    private int highScore = 0;
    private int lives = 3;
    private int level = 1;
    private long lastEnemySpawn = 0;
    private long enemySpawnInterval = 1200;
    private long lastShot = 0;
    private long shotInterval = 280;
    private float difficultyTimer = 0;

    // Entities
    private Player player;
    private List<Enemy> enemies = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private List<PowerUp> powerUps = new ArrayList<>();
    private List<Explosion> explosions = new ArrayList<>();
    private List<Star> stars = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();

    // Bitmaps
    private Bitmap bmpPlayer, bmpEnemy, bmpBullet, bmpPower, bmpHeart, bmpBg;
    private Bitmap[] bmpExplosions = new Bitmap[5];

    // Audio
    private SoundPool soundPool;
    private int sfxShoot, sfxExplosion, sfxPower, sfxHit, sfxGameOver, sfxBgm;
    private int bgmStreamId = 0;
    private boolean soundReady = false;

    // Touch
    private float touchX = -1;
    private boolean isTouching = false;

    // HUD layout constants
    private static final int HUD_MARGIN = 24;
    private static final int HUD_PANEL_H = 90;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GameView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        // Load bitmaps
        bmpPlayer = BitmapFactory.decodeResource(getResources(), R.drawable.player);
        bmpEnemy = BitmapFactory.decodeResource(getResources(), R.drawable.enemy);
        bmpBullet = BitmapFactory.decodeResource(getResources(), R.drawable.bullet);
        bmpPower = BitmapFactory.decodeResource(getResources(), R.drawable.powerup);
        bmpHeart = BitmapFactory.decodeResource(getResources(), R.drawable.heart);
        bmpBg = BitmapFactory.decodeResource(getResources(), R.drawable.bg_star);
        for (int i = 0; i < 5; i++) {
            int id = getResources().getIdentifier("explosion_" + i, "drawable", context.getPackageName());
            bmpExplosions[i] = BitmapFactory.decodeResource(getResources(), id);
        }

        // SoundPool
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
        sfxShoot = soundPool.load(context, R.raw.shoot, 1);
        sfxExplosion = soundPool.load(context, R.raw.explosion, 1);
        sfxPower = soundPool.load(context, R.raw.powerup, 1);
        sfxHit = soundPool.load(context, R.raw.hit, 1);
        sfxGameOver = soundPool.load(context, R.raw.gameover, 1);
        sfxBgm = soundPool.load(context, R.raw.bgm, 1);
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) soundReady = true;
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        screenW = getWidth();
        screenH = getHeight();
        if (player == null) {
            player = new Player(screenW / 2f, screenH - 150);
        }
        // Stars for background
        stars.clear();
        for (int i = 0; i < 60; i++) {
            stars.add(new Star(random.nextFloat() * screenW, random.nextFloat() * screenH,
                    1 + random.nextFloat() * 2.5f, 0.3f + random.nextFloat() * 0.7f));
        }
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
        screenW = width;
        screenH = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        pause();
    }

    public void resume() {
        if (running) return;
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
        if (soundReady && state == State.PLAYING && bgmStreamId == 0) {
            bgmStreamId = soundPool.play(sfxBgm, 0.35f, 0.35f, 0, -1, 1f);
        }
    }

    public void pause() {
        running = false;
        try {
            if (gameThread != null) gameThread.join(300);
        } catch (InterruptedException ignored) {}
        if (bgmStreamId != 0) {
            soundPool.stop(bgmStreamId);
            bgmStreamId = 0;
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            if (dt > 0.05f) dt = 0.05f;
            lastTime = now;

            update(dt);
            draw();
            try {
                Thread.sleep(12); // ~80fps target
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void update(float dt) {
        // Always update stars
        for (Star s : stars) {
            s.y += s.speed * 40 * dt;
            if (s.y > screenH) {
                s.y = 0;
                s.x = random.nextFloat() * screenW;
            }
        }

        if (state != State.PLAYING) return;

        difficultyTimer += dt;
        if (difficultyTimer > 15) {
            difficultyTimer = 0;
            level++;
            enemySpawnInterval = Math.max(400, enemySpawnInterval - 80);
        }

        // Player movement
        if (isTouching && touchX >= 0) {
            float targetX = touchX;
            player.x += (targetX - player.x) * 8f * dt;
        }
        player.x = Math.max(player.w / 2, Math.min(screenW - player.w / 2, player.x));

        // Auto fire
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastShot > shotInterval) {
            bullets.add(new Bullet(player.x, player.y - player.h / 2));
            lastShot = nowMs;
            if (soundReady) soundPool.play(sfxShoot, 0.4f, 0.4f, 1, 0, 1f);
        }

        // Spawn enemies
        if (nowMs - lastEnemySpawn > enemySpawnInterval) {
            float ex = 40 + random.nextFloat() * (screenW - 80);
            enemies.add(new Enemy(ex, -40, 80 + level * 12 + random.nextFloat() * 40));
            lastEnemySpawn = nowMs;
        }

        // Update bullets
        Iterator<Bullet> bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            b.y -= b.speed * dt;
            if (b.y < -20) bit.remove();
        }

        // Update enemies
        Iterator<Enemy> eit = enemies.iterator();
        while (eit.hasNext()) {
            Enemy e = eit.next();
            e.y += e.speed * dt;
            e.wobble += dt * 4;
            e.x += Math.sin(e.wobble) * 30 * dt;
            if (e.y > screenH + 50) {
                eit.remove();
                // missed, small penalty optional
            }
        }

        // Update powerups
        Iterator<PowerUp> pit = powerUps.iterator();
        while (pit.hasNext()) {
            PowerUp p = pit.next();
            p.y += p.speed * dt;
            p.rot += dt * 3;
            if (p.y > screenH + 30) pit.remove();
        }

        // Update explosions
        Iterator<Explosion> xit = explosions.iterator();
        while (xit.hasNext()) {
            Explosion x = xit.next();
            x.frameTimer += dt;
            if (x.frameTimer > 0.06f) {
                x.frameTimer = 0;
                x.frame++;
                if (x.frame >= 5) xit.remove();
            }
        }

        // Particles
        Iterator<Particle> partIt = particles.iterator();
        while (partIt.hasNext()) {
            Particle p = partIt.next();
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            p.vy += 80 * dt;
            if (p.life <= 0) partIt.remove();
        }

        // Collisions: bullets vs enemies
        bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            eit = enemies.iterator();
            boolean hit = false;
            while (eit.hasNext()) {
                Enemy e = eit.next();
                if (rectOverlap(b.x - b.w/2, b.y - b.h/2, b.w, b.h,
                        e.x - e.w/2, e.y - e.h/2, e.w, e.h)) {
                    eit.remove();
                    hit = true;
                    score += 100 * level;
                    explosions.add(new Explosion(e.x, e.y));
                    spawnParticles(e.x, e.y, 12, 0xFFFF8844);
                    if (soundReady) soundPool.play(sfxExplosion, 0.5f, 0.5f, 1, 0, 1f);
                    // chance of powerup
                    if (random.nextFloat() < 0.18f) {
                        powerUps.add(new PowerUp(e.x, e.y));
                    }
                    break;
                }
            }
            if (hit) bit.remove();
        }

        // Player vs enemies
        eit = enemies.iterator();
        while (eit.hasNext()) {
            Enemy e = eit.next();
            if (rectOverlap(player.x - player.w/2 + 8, player.y - player.h/2 + 8, player.w - 16, player.h - 16,
                    e.x - e.w/2, e.y - e.h/2, e.w, e.h)) {
                eit.remove();
                lives--;
                explosions.add(new Explosion(e.x, e.y));
                spawnParticles(player.x, player.y, 18, 0xFF44AAFF);
                if (soundReady) soundPool.play(sfxHit, 0.6f, 0.6f, 1, 0, 1f);
                if (lives <= 0) {
                    state = State.GAMEOVER;
                    if (score > highScore) highScore = score;
                    if (soundReady) {
                        soundPool.stop(bgmStreamId);
                        bgmStreamId = 0;
                        soundPool.play(sfxGameOver, 0.7f, 0.7f, 1, 0, 1f);
                    }
                }
            }
        }

        // Player vs powerups
        pit = powerUps.iterator();
        while (pit.hasNext()) {
            PowerUp p = pit.next();
            if (rectOverlap(player.x - player.w/2, player.y - player.h/2, player.w, player.h,
                    p.x - p.w/2, p.y - p.h/2, p.w, p.h)) {
                pit.remove();
                score += 250;
                lives = Math.min(5, lives + 1);
                shotInterval = Math.max(140, shotInterval - 15);
                spawnParticles(p.x, p.y, 10, 0xFF44FF88);
                if (soundReady) soundPool.play(sfxPower, 0.55f, 0.55f, 1, 0, 1f);
            }
        }
    }

    private boolean rectOverlap(float x1, float y1, float w1, float h1,
                                float x2, float y2, float w2, float h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private void spawnParticles(float x, float y, int count, int color) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2;
            float speed = 40 + random.nextFloat() * 120;
            particles.add(new Particle(x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    0.4f + random.nextFloat() * 0.5f,
                    color));
        }
    }

    private void draw() {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas == null) return;

            // Background
            canvas.drawColor(0xFF0A0A1E);
            // Tiled starfield
            if (bmpBg != null) {
                for (int y = 0; y < screenH; y += bmpBg.getHeight()) {
                    for (int x = 0; x < screenW; x += bmpBg.getWidth()) {
                        canvas.drawBitmap(bmpBg, x, y, null);
                    }
                }
            }
            // Moving stars
            paint.setColor(Color.WHITE);
            for (Star s : stars) {
                paint.setAlpha((int) (s.alpha * 255));
                canvas.drawCircle(s.x, s.y, s.size, paint);
            }
            paint.setAlpha(255);

            if (state == State.MENU) {
                drawMenu(canvas);
            } else {
                // Game entities
                // Powerups
                for (PowerUp p : powerUps) {
                    canvas.save();
                    canvas.rotate((float) Math.toDegrees(p.rot), p.x, p.y);
                    if (bmpPower != null) {
                        canvas.drawBitmap(bmpPower, p.x - bmpPower.getWidth()/2f, p.y - bmpPower.getHeight()/2f, null);
                    }
                    canvas.restore();
                }
                // Enemies
                for (Enemy e : enemies) {
                    if (bmpEnemy != null) {
                        canvas.drawBitmap(bmpEnemy, e.x - bmpEnemy.getWidth()/2f, e.y - bmpEnemy.getHeight()/2f, null);
                    }
                }
                // Bullets
                for (Bullet b : bullets) {
                    if (bmpBullet != null) {
                        canvas.drawBitmap(bmpBullet, b.x - bmpBullet.getWidth()/2f, b.y - bmpBullet.getHeight()/2f, null);
                    }
                }
                // Player
                if (player != null && bmpPlayer != null) {
                    canvas.drawBitmap(bmpPlayer, player.x - bmpPlayer.getWidth()/2f, player.y - bmpPlayer.getHeight()/2f, null);
                }
                // Explosions
                for (Explosion x : explosions) {
                    if (x.frame < 5 && bmpExplosions[x.frame] != null) {
                        Bitmap exp = bmpExplosions[x.frame];
                        canvas.drawBitmap(exp, x.x - exp.getWidth()/2f, x.y - exp.getHeight()/2f, null);
                    }
                }
                // Particles
                for (Particle p : particles) {
                    paint.setColor(p.color);
                    paint.setAlpha((int) (p.life * 400));
                    canvas.drawCircle(p.x, p.y, 3 + (1 - p.life) * 4, paint);
                }
                paint.setAlpha(255);

                // HUD
                drawHUD(canvas);

                if (state == State.GAMEOVER) {
                    drawGameOver(canvas);
                }
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawHUD(Canvas canvas) {
        // Top panel background
        panelPaint.setColor(0xCC0D1B2A);
        canvas.drawRect(0, 0, screenW, HUD_PANEL_H, panelPaint);

        // Subtle bottom line
        paint.setColor(0xFF3D9EFF);
        paint.setStrokeWidth(2);
        canvas.drawLine(0, HUD_PANEL_H, screenW, HUD_PANEL_H, paint);

        // Score
        textPaint.setTextSize(28);
        textPaint.setColor(0xFFFFD700);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("PONTOS", HUD_MARGIN, 32, textPaint);
        textPaint.setTextSize(36);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(score), HUD_MARGIN, 70, textPaint);

        // Level
        textPaint.setTextSize(22);
        textPaint.setColor(0xFF88CCFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("NIVEL " + level, screenW / 2f, 38, textPaint);

        // Lives - hearts
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(22);
        textPaint.setColor(0xFFFF6688);
        canvas.drawText("VIDAS", screenW - HUD_MARGIN, 32, textPaint);
        if (bmpHeart != null) {
            float hx = screenW - HUD_MARGIN - 10;
            for (int i = 0; i < lives; i++) {
                canvas.drawBitmap(bmpHeart, hx - bmpHeart.getWidth() - i * (bmpHeart.getWidth() + 6), 42, null);
            }
        }
    }

    private void drawMenu(Canvas canvas) {
        // Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(52);
        textPaint.setColor(0xFF3D9EFF);
        canvas.drawText("JOGUINHO", screenW / 2f, screenH * 0.28f, textPaint);
        textPaint.setTextSize(28);
        textPaint.setColor(0xFF88CCFF);
        canvas.drawText("SPACE DEFENDER", screenW / 2f, screenH * 0.28f + 48, textPaint);

        // Decorative line
        paint.setColor(0xFF3D9EFF);
        paint.setStrokeWidth(3);
        float lineW = 180;
        canvas.drawLine(screenW/2f - lineW, screenH * 0.28f + 70, screenW/2f + lineW, screenH * 0.28f + 70, paint);

        // Instructions
        textPaint.setTextSize(24);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Toque e arraste para mover", screenW / 2f, screenH * 0.48f, textPaint);
        canvas.drawText("Disparo automatico", screenW / 2f, screenH * 0.48f + 36, textPaint);
        canvas.drawText("Pegue os powerups verdes", screenW / 2f, screenH * 0.48f + 72, textPaint);

        // Start prompt
        textPaint.setTextSize(32);
        textPaint.setColor(0xFFFFD700);
        long t = System.currentTimeMillis() / 500;
        if (t % 2 == 0) {
            canvas.drawText("TOQUE PARA COMECAR", screenW / 2f, screenH * 0.72f, textPaint);
        }

        // High score
        if (highScore > 0) {
            textPaint.setTextSize(22);
            textPaint.setColor(0xFFAAAAAA);
            canvas.drawText("RECORDE: " + highScore, screenW / 2f, screenH * 0.85f, textPaint);
        }
    }

    private void drawGameOver(Canvas canvas) {
        // Dim overlay
        panelPaint.setColor(0xAA000000);
        canvas.drawRect(0, 0, screenW, screenH, panelPaint);

        // Panel
        float pw = screenW * 0.8f;
        float ph = 280;
        float px = (screenW - pw) / 2;
        float py = (screenH - ph) / 2;
        panelPaint.setColor(0xEE0D1B2A);
        canvas.drawRoundRect(new RectF(px, py, px + pw, py + ph), 24, 24, panelPaint);
        paint.setColor(0xFF3D9EFF);
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(new RectF(px, py, px + pw, py + ph), 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(40);
        textPaint.setColor(0xFFFF4466);
        canvas.drawText("FIM DE JOGO", screenW / 2f, py + 60, textPaint);

        textPaint.setTextSize(28);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Pontos: " + score, screenW / 2f, py + 120, textPaint);
        textPaint.setColor(0xFFFFD700);
        canvas.drawText("Recorde: " + highScore, screenW / 2f, py + 160, textPaint);

        textPaint.setTextSize(26);
        textPaint.setColor(0xFF88CCFF);
        canvas.drawText("Toque para reiniciar", screenW / 2f, py + 230, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                isTouching = true;
                touchX = x;
                if (state == State.MENU) {
                    startGame();
                } else if (state == State.GAMEOVER) {
                    startGame();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                touchX = -1;
                break;
        }
        return true;
    }

    private void startGame() {
        state = State.PLAYING;
        score = 0;
        lives = 3;
        level = 1;
        enemySpawnInterval = 1200;
        shotInterval = 280;
        difficultyTimer = 0;
        enemies.clear();
        bullets.clear();
        powerUps.clear();
        explosions.clear();
        particles.clear();
        player.x = screenW / 2f;
        player.y = screenH - 150;
        lastEnemySpawn = System.currentTimeMillis();
        lastShot = 0;
        if (soundReady) {
            if (bgmStreamId != 0) soundPool.stop(bgmStreamId);
            bgmStreamId = soundPool.play(sfxBgm, 0.35f, 0.35f, 0, -1, 1f);
        }
    }

    // --- Entity classes ---
    private static class Player {
        float x, y, w = 40, h = 32;
        Player(float x, float y) { this.x = x; this.y = y; }
    }

    private static class Enemy {
        float x, y, w = 32, h = 24, speed, wobble;
        Enemy(float x, float y, float speed) {
            this.x = x; this.y = y; this.speed = speed; this.wobble = 0;
        }
    }

    private static class Bullet {
        float x, y, w = 12, h = 15, speed = 620;
        Bullet(float x, float y) { this.x = x; this.y = y; }
    }

    private static class PowerUp {
        float x, y, w = 24, h = 24, speed = 90, rot;
        PowerUp(float x, float y) { this.x = x; this.y = y; }
    }

    private static class Explosion {
        float x, y;
        int frame = 0;
        float frameTimer = 0;
        Explosion(float x, float y) { this.x = x; this.y = y; }
    }

    private static class Star {
        float x, y, size, speed, alpha;
        Star(float x, float y, float size, float alpha) {
            this.x = x; this.y = y; this.size = size; this.speed = size; this.alpha = alpha;
        }
    }

    private static class Particle {
        float x, y, vx, vy, life;
        int color;
        Particle(float x, float y, float vx, float vy, float life, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.color = color;
        }
    }
}
