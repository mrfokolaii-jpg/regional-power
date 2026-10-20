package com.mrfokolaii.regionalpower;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new GameView(this));
    }

    static class GameView extends View implements Runnable {
        Thread thread; volatile boolean running;
        int W, H;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random rng = new Random();
        int mode = 0, preview = 1, unlocked = 1, xp = 0;
        JSONArray stages;
        SharedPreferences prefs;
        float px, py, pAngle, camX, camY, shake;
        int php = 100, ammo = 30, kills, need, phase;
        float objX, objY, extX, extY;
        String city = "", msg = "";
        long msgUntil;
        boolean win;
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        ArrayList<Fx> fx = new ArrayList<>();
        float sCX, sCY, sR = 100, kX, kY, mX, mY;
        int stickId = -1;
        RectF bFire, bMelee, bSlide, bReload, bSpecial;
        long lastFire, lastMelee;
        final int C_GOLD = Color.rgb(212, 175, 90);
        final int C_GREEN = Color.rgb(70, 180, 110);
        final int C_RED = Color.rgb(210, 55, 50);
        final int C_BG1 = Color.rgb(28, 42, 38);
        final int C_BG2 = Color.rgb(14, 20, 18);

        GameView(android.content.Context c) {
            super(c);
            prefs = c.getSharedPreferences("rp", 0);
            unlocked = prefs.getInt("level", 1);
            xp = prefs.getInt("xp", 0);
            preview = unlocked;
            stages = loadStages(c);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            setKeepScreenOn(true);
        }

        static JSONArray loadStages(android.content.Context c) {
            JSONArray all = new JSONArray();
            try {
                InputStream is = c.getAssets().open("stages_100.json");
                byte[] buf = new byte[is.available()]; is.read(buf); is.close();
                JSONArray one = new JSONArray(new String(buf, "UTF-8"));
                if (one.length() >= 50) return one;
            } catch (Exception ignored) {}
            for (int p = 1; p <= 4; p++) {
                try {
                    InputStream is = c.getAssets().open("stages_part" + p + ".json");
                    byte[] buf = new byte[is.available()]; is.read(buf); is.close();
                    JSONArray part = new JSONArray(new String(buf, "UTF-8"));
                    for (int i = 0; i < part.length(); i++) all.put(part.get(i));
                } catch (Exception ignored) {}
            }
            if (all.length() == 0) {
                for (int i = 1; i <= 100; i++) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("n", i); o.put("fa", "شهر " + i);
                        o.put("enemies", 2 + i / 15); o.put("xp", 100 + i * 8);
                        o.put("brief", "پاک‌سازی");
                        all.put(o);
                    } catch (Exception ignored) {}
                }
            }
            return all;
        }

        protected void onSizeChanged(int w, int h, int a, int b) {
            W = w; H = h;
            sCX = 130; sCY = H - 130; kX = sCX; kY = sCY;
            float rx = W - 150, ry = H - 150;
            bFire = oval(rx, ry, 78);
            bMelee = oval(rx - 110, ry + 10, 58);
            bSlide = oval(rx - 150, ry - 90, 52);
            bReload = oval(rx + 30, ry - 120, 48);
            bSpecial = oval(rx - 40, ry - 40, 55);
        }
        RectF oval(float cx, float cy, float r) { return new RectF(cx - r, cy - r, cx + r, cy + r); }

        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            running = true; thread = new Thread(this); thread.start();
        }
        public void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

        public void run() {
            long last = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float dt = Math.min(0.033f, (now - last) / 1e9f);
                last = now; update(dt); postInvalidate();
                try { Thread.sleep(12); } catch (Exception e) {}
            }
        }

        void update(float dt) {
            if (shake > 0) shake -= dt * 8;
            for (int i = fx.size() - 1; i >= 0; i--) if ((fx.get(i).t -= dt) <= 0) fx.remove(i);
            if (mode != 1) return;
            px = clamp(px + mX * 270 * dt, 80, 2000);
            py = clamp(py + mY * 270 * dt, 80, 1200);
            if (mX * mX + mY * mY > 0.05f) pAngle = (float) Math.atan2(mY, mX);
            camX += (px - W / 2f - camX) * Math.min(1, dt * 6);
            camY += (py - H / 2f - camY) * Math.min(1, dt * 6);
            for (int i = bullets.size() - 1; i >= 0; i--) {
                Bullet b = bullets.get(i);
                b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt;
                boolean hit = false;
                for (Enemy e : enemies) {
                    if (e.hp > 0 && dist(b.x, b.y, e.x, e.y) < 32) {
                        e.hp -= b.dmg; hit = true;
                        fx.add(new Fx(e.x, e.y, 0.28f, 1)); shake = 0.12f;
                        if (e.hp <= 0) { kills++; e.hp = 0; fx.add(new Fx(e.x, e.y, 0.45f, 2)); }
                        break;
                    }
                }
                if (hit || b.life <= 0) bullets.remove(i);
            }
            if (phase == 0 && dist(px, py, objX, objY) < 50) {
                phase = 1; spawn(); toast("دشمن را پاک کن");
            } else if (phase == 1) {
                for (Enemy e : enemies) {
                    if (e.hp <= 0) continue;
                    e.update(dt, px, py);
                    if (dist(px, py, e.x, e.y) < 38 && System.currentTimeMillis() - e.lastHit > 700) {
                        e.lastHit = System.currentTimeMillis(); php -= e.dmg; shake = 0.22f;
                        fx.add(new Fx(px, py, 0.25f, 1));
                        if (php <= 0) { php = 0; mode = 2; win = false; }
                    }
                }
                for (int i = enemies.size() - 1; i >= 0; i--)
                    if (enemies.get(i).hp <= 0) enemies.remove(i);
                if (kills >= need) { phase = 2; toast("به نقطه سبز برو"); }
            } else if (phase == 2 && dist(px, py, extX, extY) < 50) {
                mode = 2; win = true;
                try {
                    int gain = stages.getJSONObject(preview - 1).optInt("xp", 100);
                    xp += gain;
                    if (preview >= unlocked) unlocked = Math.min(100, preview + 1);
                    prefs.edit().putInt("level", unlocked).putInt("xp", xp).apply();
                } catch (Exception e) {}
            }
        }

        void spawn() {
            enemies.clear();
            for (int i = 0; i < need; i++) {
                Enemy e = new Enemy();
                e.x = objX + rng.nextFloat() * 280 - 140;
                e.y = objY + 100 + rng.nextFloat() * 160;
                e.hp = e.max = 35 + preview * 2;
                e.spd = 85 + preview * 1.2f;
                e.dmg = 7 + preview / 12;
                e.type = i % 3;
                enemies.add(e);
            }
        }

        void startStage(int n) {
            preview = n;
            try {
                JSONObject s = stages.getJSONObject(n - 1);
                city = s.optString("fa", "شهر");
                need = Math.max(2, s.optInt("enemies", 3));
                toast(s.optString("brief", "پاک سازی"));
            } catch (Exception e) { city = "مرحله " + n; need = 3; }
            kills = 0; phase = 0; php = 100; ammo = 30;
            px = 200; py = 400; objX = 900; objY = 450; extX = 250; extY = 900;
            enemies.clear(); bullets.clear(); fx.clear();
            mode = 1;
        }

        void toast(String m) { msg = m; msgUntil = System.currentTimeMillis() + 3200; }

        protected void onDraw(Canvas c) {
            if (W == 0) return;
            p.setShader(new LinearGradient(0, 0, 0, H, C_BG1, C_BG2, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            if (mode == 0) drawMenu(c);
            else if (mode == 1) drawPlay(c);
            else drawResult(c);
        }

        void drawMenu(Canvas c) {
            p.setColor(Color.argb(160, 0, 0, 0));
            c.drawRoundRect(28, 28, W - 28, H - 28, 22, 22, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(C_GOLD);
            c.drawRoundRect(28, 28, W - 28, H - 28, 22, 22, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(52); text.setColor(C_GOLD);
            c.drawText("Regional Power", 56, 95, text);
            text.setTextSize(26); text.setColor(Color.rgb(190, 200, 195));
            c.drawText("پاک‌سازی ۱۰۰ شهر ایران — دوبعدی", 56, 138, text);
            c.drawText("پیشرفت " + unlocked + "/100    XP " + xp, 56, 178, text);
            String name = "مرحله " + preview;
            try { name = preview + " — " + stages.getJSONObject(preview - 1).optString("fa"); } catch (Exception e) {}
            boolean lock = preview > unlocked;
            text.setTextSize(36); text.setColor(lock ? Color.GRAY : Color.WHITE);
            c.drawText(name + (lock ? "  [قفل]" : ""), 56, 250, text);
            String brief = "";
            try { brief = stages.getJSONObject(preview - 1).optString("brief"); } catch (Exception e) {}
            text.setTextSize(22); text.setColor(Color.LTGRAY);
            c.drawText(brief, 56, 295, text);
            drawBtn(c, W * 0.08f, H - 140, 160, 64, "قبلی");
            drawBtn(c, W * 0.35f, H - 140, 220, 64, lock ? "قفل" : "شروع");
            drawBtn(c, W * 0.68f, H - 140, 160, 64, "بعدی");
        }

        void drawPlay(Canvas c) {
            c.save();
            float sx = shake > 0 ? (rng.nextFloat() - 0.5f) * 10 * shake : 0;
            float sy = shake > 0 ? (rng.nextFloat() - 0.5f) * 10 * shake : 0;
            c.translate(-camX + sx, -camY + sy);
            int gs = 64;
            int x0 = ((int) camX / gs) * gs - gs;
            int y0 = ((int) camY / gs) * gs - gs;
            for (int x = x0; x < camX + W + gs; x += gs)
                for (int y = y0; y < camY + H + gs; y += gs) {
                    p.setColor((((x / gs) + (y / gs)) % 2 == 0) ? Color.rgb(38, 56, 50) : Color.rgb(32, 48, 44));
                    c.drawRect(x, y, x + gs, y + gs, p);
                }
            for (int i = 0; i < 14; i++) {
                float bx = 120 + i * 150, by = 80 + (i % 4) * 70;
                p.setColor(Color.rgb(24, 34, 40));
                c.drawRoundRect(bx, by, bx + 95, by + 150, 6, 6, p);
                p.setColor(Color.argb(50, 220, 200, 80));
                c.drawRect(bx + 12, by + 18, bx + 32, by + 38, p);
                c.drawRect(bx + 50, by + 18, bx + 70, by + 38, p);
            }
            if (phase == 0) drawMarker(c, objX, objY, Color.rgb(255, 200, 50), "هدف");
            if (phase == 2) drawMarker(c, extX, extY, Color.rgb(50, 230, 130), "استخراج");
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                int col = e.type == 1 ? C_RED : e.type == 2 ? Color.rgb(70, 110, 210) : Color.rgb(160, 110, 60);
                drawUnit(c, e.x, e.y, col, false);
                float r = e.hp / (float) e.max;
                p.setColor(Color.argb(170, 0, 0, 0));
                c.drawRect(e.x - 22, e.y - 42, e.x + 22, e.y - 36, p);
                p.setColor(r > 0.4f ? C_GREEN : C_RED);
                c.drawRect(e.x - 22, e.y - 42, e.x - 22 + 44 * r, e.y - 36, p);
            }
            drawUnit(c, px, py, C_GREEN, true);
            p.setColor(C_GOLD);
            c.drawCircle(px + (float) Math.cos(pAngle) * 24, py + (float) Math.sin(pAngle) * 24, 5, p);
            p.setColor(Color.rgb(255, 230, 120));
            for (Bullet b : bullets) c.drawCircle(b.x, b.y, 4.5f, p);
            for (Fx f : fx) {
                int a = (int) (255 * Math.max(0, f.t / 0.5f));
                p.setColor(Color.argb(a, 255, f.kind == 2 ? 90 : 160, 40));
                c.drawCircle(f.x, f.y, 12 + (0.5f - f.t) * 36, p);
            }
            c.restore();
            p.setColor(Color.argb(210, 6, 10, 12)); c.drawRect(0, 0, W, 74, p);
            text.setTextSize(24); text.setColor(Color.WHITE); c.drawText("HP", 20, 30, text);
            p.setColor(Color.rgb(40, 40, 40)); c.drawRoundRect(60, 14, 210, 32, 6, 6, p);
            p.setColor(php > 40 ? C_GREEN : C_RED); c.drawRoundRect(60, 14, 60 + php * 1.5f, 32, 6, 6, p);
            text.setColor(C_GOLD); c.drawText("XP " + xp, W - 130, 32, text);
            text.setColor(Color.rgb(180, 190, 185)); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(city + "  |  " + kills + "/" + need + "  |  گلوله " + ammo, W / 2f, 32, text);
            text.setTextAlign(Paint.Align.LEFT);
            if (System.currentTimeMillis() < msgUntil) {
                p.setColor(Color.argb(170, 0, 0, 0));
                c.drawRoundRect(W / 2f - 200, H * 0.16f - 28, W / 2f + 200, H * 0.16f + 28, 12, 12, p);
                text.setTextAlign(Paint.Align.CENTER); text.setTextSize(26); text.setColor(Color.WHITE);
                c.drawText(msg, W / 2f, H * 0.16f + 8, text); text.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(Color.argb(50, 255, 255, 255)); c.drawCircle(sCX, sCY, sR, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(130, 255, 255, 255));
            c.drawCircle(sCX, sCY, sR, p); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(210, 235, 235, 235)); c.drawCircle(kX, kY, 36, p);
            drawAct(c, bFire, "شلیک", C_GOLD);
            drawAct(c, bMelee, "مشت", Color.rgb(220, 120, 80));
            drawAct(c, bSlide, "اسلاید", Color.rgb(100, 160, 220));
            drawAct(c, bReload, "گلوله", Color.rgb(150, 150, 150));
            drawAct(c, bSpecial, "ویژه", Color.rgb(200, 90, 180));
        }

        void drawUnit(Canvas c, float x, float y, int col, boolean player) {
            p.setColor(Color.argb(55, 0, 0, 0));
            c.drawOval(x - 18, y + 14, x + 18, y + 26, p);
            p.setColor(col); c.drawCircle(x, y, player ? 26 : 23, p);
            p.setColor(Color.argb(70, 255, 255, 255));
            c.drawCircle(x - 6, y - 7, player ? 9 : 7, p);
            if (player) { p.setColor(C_GOLD); c.drawCircle(x, y - 22, 6, p); }
        }

        void drawMarker(Canvas c, float x, float y, int col, String lab) {
            p.setColor(Color.argb(55, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawCircle(x, y, 50, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(col);
            c.drawCircle(x, y, 50, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(18); text.setColor(col); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x, y - 60, text); text.setTextAlign(Paint.Align.LEFT);
        }

        void drawAct(Canvas c, RectF r, String lab, int col) {
            p.setColor(Color.argb(130, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawOval(r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
            p.setColor(Color.argb(200, 255, 255, 255)); c.drawOval(r, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(15); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, r.centerX(), r.centerY() + 5, text); text.setTextAlign(Paint.Align.LEFT);
        }

        void drawResult(Canvas c) {
            p.setColor(Color.argb(200, 0, 0, 0));
            c.drawRoundRect(W / 2f - 220, H / 2f - 120, W / 2f + 220, H / 2f + 140, 18, 18, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(48); text.setColor(win ? C_GREEN : C_RED);
            c.drawText(win ? "پیروزی" : "شکست", W / 2f, H / 2f - 40, text);
            text.setTextSize(24); text.setColor(Color.WHITE);
            c.drawText(win ? city + " پاک شد" : "دوباره تلاش کن", W / 2f, H / 2f + 10, text);
            text.setTextAlign(Paint.Align.LEFT);
            drawBtn(c, W / 2f - 110, H / 2f + 50, 220, 56, "منو");
        }

        void drawBtn(Canvas c, float x, float y, float w, float h, String lab) {
            p.setColor(Color.rgb(36, 68, 52));
            c.drawRoundRect(x, y, x + w, y + h, 14, 14, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(C_GOLD);
            c.drawRoundRect(x, y, x + w, y + h, 14, 14, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(26); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x + w / 2, y + h / 2 + 9, text); text.setTextAlign(Paint.Align.LEFT);
        }

        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked(), idx = e.getActionIndex();
            float x = e.getX(idx), y = e.getY(idx); int id = e.getPointerId(idx);
            if (mode == 0) {
                if (action == MotionEvent.ACTION_UP && y > H - 140 && y < H - 76) {
                    if (x > W * 0.08f && x < W * 0.08f + 160) preview = Math.max(1, preview - 1);
                    else if (x > W * 0.35f && x < W * 0.35f + 220) {
                        if (preview <= unlocked) startStage(preview);
                    } else if (x > W * 0.68f && x < W * 0.68f + 160) preview = Math.min(100, preview + 1);
                }
                return true;
            }
            if (mode == 2) {
                if (action == MotionEvent.ACTION_UP && y > H / 2f + 50 && y < H / 2f + 110) mode = 0;
                return true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (x < W * 0.42f) { stickId = id; moveStick(x, y); } else press(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++)
                        if (e.getPointerId(i) == stickId) moveStick(e.getX(i), e.getY(i));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (id == stickId) { stickId = -1; mX = mY = 0; kX = sCX; kY = sCY; }
                    break;
            }
            return true;
        }

        void moveStick(float x, float y) {
            float dx = x - sCX, dy = y - sCY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > sR) { dx = dx / len * sR; dy = dy / len * sR; }
            kX = sCX + dx; kY = sCY + dy; mX = dx / sR; mY = dy / sR;
        }
        void press(float x, float y) {
            if (bFire.contains(x, y)) fire();
            else if (bMelee.contains(x, y) || bSpecial.contains(x, y)) melee();
            else if (bReload.contains(x, y)) { ammo = 30; toast("پر شد"); }
            else if (bSlide.contains(x, y)) { px += mX * 90; py += mY * 90; }
        }
        void fire() {
            long t = System.currentTimeMillis();
            if (t - lastFire < 160 || ammo <= 0) return;
            lastFire = t; ammo--;
            Bullet b = new Bullet(); b.x = px; b.y = py;
            float a = pAngle;
            Enemy best = null; float bd = 400;
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                float d = dist(px, py, e.x, e.y);
                if (d < bd) { bd = d; best = e; }
            }
            if (best != null) a = (float) Math.atan2(best.y - py, best.x - px);
            b.vx = (float) Math.cos(a) * 620; b.vy = (float) Math.sin(a) * 620;
            b.dmg = 18 + preview / 5; b.life = 0.9f; bullets.add(b);
            fx.add(new Fx(px + (float) Math.cos(a) * 28, py + (float) Math.sin(a) * 28, 0.12f, 1));
        }
        void melee() {
            long t = System.currentTimeMillis();
            if (t - lastMelee < 280) return;
            lastMelee = t;
            for (Enemy e : enemies) {
                if (e.hp > 0 && dist(px, py, e.x, e.y) < 75) {
                    e.hp -= 30; fx.add(new Fx(e.x, e.y, 0.3f, 1)); shake = 0.18f;
                    if (e.hp <= 0) kills++;
                }
            }
        }
        float dist(float ax, float ay, float bx, float by) {
            float dx = ax - bx, dy = ay - by;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
        float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

        static class Enemy {
            float x, y, spd; int hp, max, dmg, type; long lastHit;
            void update(float dt, float tx, float ty) {
                float dx = tx - x, dy = ty - y;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1) return;
                float prefer = type == 2 ? 170 : 45;
                if (type == 2 && len < prefer) { dx = -dx; dy = -dy; len = (float) Math.sqrt(dx * dx + dy * dy); }
                x += dx / len * spd * dt; y += dy / len * spd * dt;
            }
        }
        static class Bullet { float x, y, vx, vy, life; int dmg; }
        static class Fx {
            float x, y, t; int kind;
            Fx(float x, float y, float t, int k) { this.x = x; this.y = y; this.t = t; kind = k; }
        }
    }
}
