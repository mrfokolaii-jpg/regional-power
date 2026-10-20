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
        int mode = 0, preview = 1, unlocked = 1, xp = 0, selected = 0;
        JSONArray stages;
        SharedPreferences prefs;
        float px, py, camX, camY, shake, jumpV;
        int php = 100, ammo = 0, kills, need, phase, weapon;
        float objX, objY, extX, extY;
        String city = "", msg = "";
        long msgUntil, lastPunch, lastKick, lastJump, lastSpecial;
        boolean win, facingRight = true, onGround = true, shielding;
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        ArrayList<Fx> fx = new ArrayList<>();
        float sCX, sCY, sR = 95, kX, kY, mX, mY;
        int stickId = -1;
        RectF bPunch, bKick, bJump, bSlide, bSpecial, bShield;
        final int C_GOLD = Color.rgb(220, 180, 70);
        final int C_GREEN = Color.rgb(70, 185, 120);
        final int C_RED = Color.rgb(210, 55, 50);

        static final String[] CHAR_FA = {"آرش", "رستم", "سارا", "ایوان", "لی", "کیم"};
        static final String[] CHAR_NAT = {"ایران", "ایران", "ایران", "روسیه", "چین", "کره شمالی"};
        static final int[] CHAR_COL = {
            Color.rgb(40, 140, 90), Color.rgb(180, 70, 50), Color.rgb(70, 100, 180),
            Color.rgb(50, 90, 160), Color.rgb(200, 50, 50), Color.rgb(60, 60, 70)
        };
        static final int[] CHAR_PWR = {3, 5, 2, 4, 3, 4};
        static final int[] CHAR_SPD = {4, 2, 5, 3, 4, 3};

        GameView(android.content.Context c) {
            super(c);
            prefs = c.getSharedPreferences("rp", 0);
            unlocked = prefs.getInt("level", 1);
            xp = prefs.getInt("xp", 0);
            selected = prefs.getInt("char", 0);
            preview = unlocked;
            stages = loadStages(c);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            setKeepScreenOn(true);
        }

        static JSONArray loadStages(android.content.Context c) {
            JSONArray all = new JSONArray();
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
                        o.put("enemies", 2 + i / 12); o.put("xp", 100 + i * 8);
                        o.put("brief", "پاک‌سازی"); all.put(o);
                    } catch (Exception ignored) {}
                }
            }
            return all;
        }

        protected void onSizeChanged(int w, int h, int a, int b) {
            W = w; H = h;
            sCX = 120; sCY = H - 120; kX = sCX; kY = sCY;
            float rx = W - 130, ry = H - 140;
            bPunch = oval(rx + 10, ry + 10, 52);
            bKick = oval(rx - 70, ry + 25, 44);
            bJump = oval(rx - 20, ry - 70, 42);
            bSlide = oval(rx - 100, ry - 40, 40);
            bSpecial = oval(rx + 35, ry - 75, 40);
            bShield = oval(rx - 55, ry - 100, 36);
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
            if (mode != 2) return;
            float spd = 200 + CHAR_SPD[selected] * 18;
            px = clamp(px + mX * spd * dt, 60, 2200);
            if (mX > 0.1f) facingRight = true;
            if (mX < -0.1f) facingRight = false;
            if (!onGround) {
                jumpV += 1400 * dt; py += jumpV * dt;
                if (py >= 520) { py = 520; jumpV = 0; onGround = true; }
            } else py = 520;
            camX += (px - W / 2f - camX) * Math.min(1, dt * 5);
            camY = 80;
            for (int i = bullets.size() - 1; i >= 0; i--) {
                Bullet b = bullets.get(i);
                b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt;
                boolean hit = false;
                for (Enemy e : enemies) {
                    if (e.hp > 0 && dist(b.x, b.y, e.x, e.y) < 30) {
                        e.hp -= b.dmg; hit = true;
                        fx.add(new Fx(e.x, e.y - 20, 0.25f, 1));
                        if (e.hp <= 0) { kills++; e.hp = 0; fx.add(new Fx(e.x, e.y, 0.4f, 2)); }
                        break;
                    }
                }
                if (hit || b.life <= 0) bullets.remove(i);
            }
            if (phase == 0 && dist(px, py, objX, objY) < 55) {
                phase = 1; spawn(); toast("دشمن را نابود کن");
            } else if (phase == 1) {
                for (Enemy e : enemies) {
                    if (e.hp <= 0) continue;
                    e.update(dt, px, py);
                    if (!shielding && dist(px, py, e.x, e.y) < 42
                            && System.currentTimeMillis() - e.lastHit > 650) {
                        e.lastHit = System.currentTimeMillis();
                        php -= e.dmg; shake = 0.25f;
                        fx.add(new Fx(px, py - 30, 0.2f, 1));
                        if (php <= 0) { php = 0; mode = 3; win = false; }
                    }
                }
                for (int i = enemies.size() - 1; i >= 0; i--)
                    if (enemies.get(i).hp <= 0) enemies.remove(i);
                if (kills >= need) { phase = 2; toast("به نقطه سبز برو"); }
            } else if (phase == 2 && dist(px, py, extX, extY) < 55) {
                mode = 3; win = true;
                try {
                    int gain = stages.getJSONObject(preview - 1).optInt("xp", 100);
                    xp += gain;
                    if (preview >= unlocked) unlocked = Math.min(100, preview + 1);
                    prefs.edit().putInt("level", unlocked).putInt("xp", xp).putInt("char", selected).apply();
                } catch (Exception e) {}
            }
            shielding = false;
        }

        void spawn() {
            enemies.clear();
            for (int i = 0; i < need; i++) {
                Enemy e = new Enemy();
                e.x = objX + rng.nextFloat() * 320 - 160; e.y = 520;
                e.hp = e.max = 40 + preview * 3;
                e.spd = 70 + preview * 1.5f; e.dmg = 8 + preview / 10;
                e.type = i % 3; e.weapon = preview >= 40 ? 2 : (preview >= 15 ? 1 : 0);
                enemies.add(e);
            }
        }

        void startStage(int n) {
            preview = n;
            try {
                JSONObject s = stages.getJSONObject(n - 1);
                city = s.optString("fa", "شهر");
                need = Math.max(2, s.optInt("enemies", 3));
                toast(s.optString("brief", "پاک‌سازی"));
            } catch (Exception e) { city = "مرحله " + n; need = 3; }
            weapon = n >= 40 ? 2 : (n >= 15 ? 1 : 0);
            ammo = weapon == 2 ? 20 : 0;
            kills = 0; phase = 0; php = 100;
            px = 180; py = 520; objX = 950; objY = 520; extX = 220; extY = 520;
            enemies.clear(); bullets.clear(); fx.clear();
            onGround = true; jumpV = 0; mode = 2;
        }

        void toast(String m) { msg = m; msgUntil = System.currentTimeMillis() + 2800; }

        protected void onDraw(Canvas c) {
            if (W == 0) return;
            if (mode == 0) drawMenu(c);
            else if (mode == 1) drawCharSelect(c);
            else if (mode == 2) drawPlay(c);
            else drawResult(c);
        }

        void drawMenu(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(12, 18, 28), Color.rgb(8, 10, 14), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            for (int i = 0; i < 12; i++) {
                float bx = i * (W / 11f);
                p.setColor(Color.rgb(20, 28, 40));
                c.drawRect(bx, H * 0.35f + (i % 3) * 30, bx + W / 12f, H * 0.7f, p);
            }
            p.setColor(Color.argb(200, 0, 0, 0));
            c.drawRoundRect(40, 40, W - 40, H - 40, 20, 20, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(C_GOLD);
            c.drawRoundRect(40, 40, W - 40, H - 40, 20, 20, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(50); text.setColor(C_GOLD);
            c.drawText("Regional Power", 70, 110, text);
            text.setTextSize(24); text.setColor(Color.rgb(180, 190, 200));
            c.drawText("پاک‌سازی ۱۰۰ شهر — سبک مبارزه‌ای", 70, 150, text);
            c.drawText("پیشرفت " + unlocked + "/100   XP " + xp, 70, 190, text);
            String name = preview + " — شهر";
            try { name = preview + " — " + stages.getJSONObject(preview - 1).optString("fa"); } catch (Exception e) {}
            boolean lock = preview > unlocked;
            text.setTextSize(34); text.setColor(lock ? Color.GRAY : Color.WHITE);
            c.drawText(name + (lock ? " [قفل]" : ""), 70, 260, text);
            text.setTextSize(22); text.setColor(C_GOLD);
            c.drawText("شخصیت: " + CHAR_FA[selected] + " (" + CHAR_NAT[selected] + ")", 70, 320, text);
            drawBtn(c, W * 0.06f, H - 130, 140, 56, "قبلی");
            drawBtn(c, W * 0.28f, H - 130, 160, 56, "شخصیت");
            drawBtn(c, W * 0.52f, H - 130, 160, 56, lock ? "قفل" : "شروع");
            drawBtn(c, W * 0.76f, H - 130, 140, 56, "بعدی");
        }

        void drawCharSelect(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(10, 20, 40), Color.rgb(5, 8, 16), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            text.setTextSize(42); text.setColor(C_GOLD); text.setTextAlign(Paint.Align.CENTER);
            c.drawText("انتخاب مبارز", W / 2f, 70, text); text.setTextAlign(Paint.Align.LEFT);
            float cellW = (W - 80) / 3f, cellH = 130;
            for (int i = 0; i < 6; i++) {
                int col = i % 3, row = i / 3;
                float x = 40 + col * cellW, y = 110 + row * (cellH + 20);
                boolean sel = i == selected;
                p.setColor(sel ? Color.rgb(50, 70, 40) : Color.rgb(25, 30, 40));
                c.drawRoundRect(x, y, x + cellW - 12, y + cellH, 12, 12, p);
                if (sel) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(C_GOLD);
                    c.drawRoundRect(x, y, x + cellW - 12, y + cellH, 12, 12, p); p.setStyle(Paint.Style.FILL);
                }
                drawFighter(c, x + cellW / 2 - 6, y + 70, CHAR_COL[i], true, 1.1f, false);
                text.setTextSize(20); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
                c.drawText(CHAR_FA[i], x + cellW / 2 - 6, y + 110, text);
                text.setTextSize(14); text.setColor(Color.rgb(160, 170, 180));
                c.drawText(CHAR_NAT[i], x + cellW / 2 - 6, y + 128, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
            text.setTextSize(22); text.setColor(Color.WHITE);
            c.drawText("قدرت " + stars(CHAR_PWR[selected]) + "   سرعت " + stars(CHAR_SPD[selected]), 50, H - 100, text);
            drawBtn(c, W / 2f - 100, H - 70, 200, 50, "تأیید");
        }

        String stars(int n) {
            String s = ""; for (int i = 0; i < 5; i++) s += i < n ? "★" : "☆"; return s;
        }

        void drawPlay(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(18, 28, 45), Color.rgb(12, 16, 22), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            c.save();
            float sx = shake > 0 ? (rng.nextFloat() - 0.5f) * 12 * shake : 0;
            c.translate(-camX + sx, -camY);
            p.setColor(Color.rgb(35, 40, 48));
            c.drawRect(camX - 50, 540, camX + W + 50, 700, p);
            p.setColor(Color.rgb(55, 58, 65));
            for (int i = 0; i < 30; i++) c.drawRect(i * 90f, 555, i * 90f + 50, 562, p);
            for (int i = 0; i < 16; i++) {
                float bx = i * 160f; int h = 180 + (i * 37) % 120;
                p.setColor(Color.rgb(22 + (i % 3) * 4, 28, 40));
                c.drawRect(bx, 540 - h, bx + 120, 540, p);
                p.setColor(Color.argb(80, 255, 220, 100));
                for (int wy = 0; wy < 4; wy++) for (int wx = 0; wx < 3; wx++)
                    c.drawRect(bx + 15 + wx * 30, 540 - h + 20 + wy * 35, bx + 30 + wx * 30, 540 - h + 35 + wy * 35, p);
            }
            if (phase == 0) drawMarker(c, objX, objY - 40, Color.rgb(255, 200, 50), "هدف");
            if (phase == 2) drawMarker(c, extX, extY - 40, Color.rgb(50, 230, 130), "استخراج");
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                int col = e.type == 1 ? Color.rgb(160, 50, 50) : e.type == 2 ? Color.rgb(50, 80, 160) : Color.rgb(120, 90, 50);
                drawFighter(c, e.x, e.y, col, e.x < px, 1f, e.weapon > 0);
                float r = e.hp / (float) e.max;
                p.setColor(Color.argb(160, 0, 0, 0)); c.drawRect(e.x - 24, e.y - 95, e.x + 24, e.y - 88, p);
                p.setColor(r > 0.35f ? C_GREEN : C_RED); c.drawRect(e.x - 24, e.y - 95, e.x - 24 + 48 * r, e.y - 88, p);
            }
            drawFighter(c, px, py, CHAR_COL[selected], facingRight, 1.15f, weapon > 0);
            for (Bullet b : bullets) { p.setColor(Color.rgb(255, 230, 100)); c.drawCircle(b.x, b.y, 5, p); }
            for (Fx f : fx) {
                int a = (int) (220 * Math.max(0, f.t / 0.45f));
                p.setColor(Color.argb(a, 255, f.kind == 2 ? 80 : 150, 40));
                c.drawCircle(f.x, f.y, 10 + (0.45f - f.t) * 40, p);
            }
            c.restore();
            p.setColor(Color.argb(220, 5, 8, 12)); c.drawRect(0, 0, W, 64, p);
            text.setTextSize(18); text.setColor(Color.WHITE); c.drawText("HP", 16, 28, text);
            p.setColor(Color.rgb(40, 40, 45)); c.drawRoundRect(48, 14, 200, 32, 5, 5, p);
            p.setColor(php > 35 ? C_GREEN : C_RED); c.drawRoundRect(48, 14, 48 + php * 1.5f, 32, 5, 5, p);
            text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.rgb(200, 210, 220));
            String wpn = weapon == 2 ? "اسلحه" : weapon == 1 ? "چاقو" : "دست‌خالی";
            c.drawText(city + " | " + kills + "/" + need + " | " + wpn + (weapon == 2 ? " " + ammo : ""), W / 2f, 28, text);
            text.setTextAlign(Paint.Align.LEFT); text.setColor(C_GOLD); c.drawText("XP " + xp, W - 110, 28, text);
            text.setTextSize(16); text.setColor(Color.LTGRAY); c.drawText(CHAR_FA[selected], 16, 52, text);
            if (System.currentTimeMillis() < msgUntil) {
                p.setColor(Color.argb(180, 0, 0, 0));
                c.drawRoundRect(W / 2f - 180, H * 0.14f - 24, W / 2f + 180, H * 0.14f + 24, 10, 10, p);
                text.setTextAlign(Paint.Align.CENTER); text.setTextSize(24); text.setColor(Color.WHITE);
                c.drawText(msg, W / 2f, H * 0.14f + 8, text); text.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(Color.argb(40, 255, 255, 255)); c.drawCircle(sCX, sCY, sR, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(120, 255, 255, 255));
            c.drawCircle(sCX, sCY, sR, p); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(220, 240, 240, 245)); c.drawCircle(kX, kY, 34, p);
            drawAct(c, bPunch, "مشت", Color.rgb(220, 100, 70));
            drawAct(c, bKick, "لگد", Color.rgb(200, 140, 60));
            drawAct(c, bJump, "پرش", Color.rgb(90, 150, 220));
            drawAct(c, bSlide, "سر", Color.rgb(100, 180, 160));
            drawAct(c, bSpecial, "ویژه", Color.rgb(200, 80, 180));
            drawAct(c, bShield, "سپر", Color.rgb(120, 140, 180));
        }

        void drawFighter(Canvas c, float x, float y, int col, boolean faceRight, float scale, boolean armed) {
            float s = scale; float dir = faceRight ? 1 : -1;
            p.setColor(Color.argb(50, 0, 0, 0));
            c.drawOval(x - 22 * s, y + 2, x + 22 * s, y + 12, p);
            p.setColor(Color.rgb(Color.red(col) / 2, Color.green(col) / 2, Color.blue(col) / 2));
            c.drawRoundRect(x - 10 * s, y - 28 * s, x - 2 * s, y + 2, 3, 3, p);
            c.drawRoundRect(x + 2 * s, y - 28 * s, x + 10 * s, y + 2, 3, 3, p);
            p.setColor(col);
            c.drawRoundRect(x - 14 * s, y - 58 * s, x + 14 * s, y - 26 * s, 6, 6, p);
            p.setColor(Color.rgb(210, 170, 140));
            c.drawCircle(x, y - 70 * s, 13 * s, p);
            p.setColor(Color.rgb(30, 30, 35));
            c.drawArc(x - 13 * s, y - 82 * s, x + 13 * s, y - 62 * s, 200, 140, false, p);
            p.setColor(col);
            c.drawRoundRect(x - 22 * s * dir - 6, y - 54 * s, x - 22 * s * dir + 6, y - 30 * s, 4, 4, p);
            c.drawRoundRect(x + 8 * s * dir - 4, y - 52 * s, x + 8 * s * dir + 8, y - 28 * s, 4, 4, p);
            p.setColor(Color.rgb(230, 190, 160));
            c.drawCircle(x + 22 * s * dir, y - 32 * s, 7 * s, p);
            if (armed) {
                p.setColor(Color.rgb(180, 180, 190));
                c.drawRect(x + 18 * s * dir, y - 48 * s, x + 36 * s * dir, y - 44 * s, p);
            }
        }

        void drawMarker(Canvas c, float x, float y, int col, String lab) {
            p.setColor(Color.argb(50, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawCircle(x, y + 40, 40, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(col);
            c.drawCircle(x, y + 40, 40, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(16); text.setColor(col); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x, y, text); text.setTextAlign(Paint.Align.LEFT);
        }

        void drawAct(Canvas c, RectF r, String lab, int col) {
            p.setColor(Color.argb(140, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawOval(r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f);
            p.setColor(Color.argb(200, 255, 255, 255)); c.drawOval(r, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(13); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, r.centerX(), r.centerY() + 4, text); text.setTextAlign(Paint.Align.LEFT);
        }

        void drawResult(Canvas c) {
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRoundRect(W / 2f - 200, H / 2f - 110, W / 2f + 200, H / 2f + 120, 16, 16, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(46); text.setColor(win ? C_GREEN : C_RED);
            c.drawText(win ? "پیروزی" : "شکست", W / 2f, H / 2f - 40, text);
            text.setTextSize(22); text.setColor(Color.WHITE);
            c.drawText(win ? city + " پاک شد" : "دوباره تلاش کن", W / 2f, H / 2f + 5, text);
            text.setTextAlign(Paint.Align.LEFT);
            drawBtn(c, W / 2f - 100, H / 2f + 45, 200, 50, "منو");
        }

        void drawBtn(Canvas c, float x, float y, float w, float h, String lab) {
            p.setColor(Color.rgb(30, 55, 45));
            c.drawRoundRect(x, y, x + w, y + h, 12, 12, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(C_GOLD);
            c.drawRoundRect(x, y, x + w, y + h, 12, 12, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(22); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x + w / 2, y + h / 2 + 8, text); text.setTextAlign(Paint.Align.LEFT);
        }

        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked(), idx = e.getActionIndex();
            float x = e.getX(idx), y = e.getY(idx); int id = e.getPointerId(idx);
            if (mode == 0) {
                if (action == MotionEvent.ACTION_UP && y > H - 130 && y < H - 74) {
                    if (x > W * 0.06f && x < W * 0.06f + 140) preview = Math.max(1, preview - 1);
                    else if (x > W * 0.28f && x < W * 0.28f + 160) mode = 1;
                    else if (x > W * 0.52f && x < W * 0.52f + 160) {
                        if (preview <= unlocked) startStage(preview);
                    } else if (x > W * 0.76f && x < W * 0.76f + 140) preview = Math.min(100, preview + 1);
                }
                return true;
            }
            if (mode == 1) {
                if (action == MotionEvent.ACTION_UP) {
                    float cellW = (W - 80) / 3f, cellH = 130;
                    for (int i = 0; i < 6; i++) {
                        int col = i % 3, row = i / 3;
                        float cx = 40 + col * cellW, cy = 110 + row * (cellH + 20);
                        if (x > cx && x < cx + cellW - 12 && y > cy && y < cy + cellH) selected = i;
                    }
                    if (y > H - 70 && y < H - 20 && x > W / 2f - 100 && x < W / 2f + 100) {
                        prefs.edit().putInt("char", selected).apply(); mode = 0;
                    }
                }
                return true;
            }
            if (mode == 3) {
                if (action == MotionEvent.ACTION_UP && y > H / 2f + 45 && y < H / 2f + 95) mode = 0;
                return true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (x < W * 0.38f) { stickId = id; moveStick(x, y); } else press(x, y);
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
            kX = sCX + dx; kY = sCY + dy; mX = dx / sR; mY = 0;
        }

        void press(float x, float y) {
            if (bPunch.contains(x, y)) punch();
            else if (bKick.contains(x, y)) kick();
            else if (bJump.contains(x, y)) jump();
            else if (bSlide.contains(x, y)) slide();
            else if (bSpecial.contains(x, y)) special();
            else if (bShield.contains(x, y)) { shielding = true; toast("سپر"); }
        }

        void punch() {
            long t = System.currentTimeMillis();
            if (t - lastPunch < 220) return; lastPunch = t;
            if (weapon == 2 && ammo > 0) { shoot(); return; }
            hitFront(70, 18 + CHAR_PWR[selected] * 3 + (weapon == 1 ? 12 : 0), 0.25f);
        }
        void kick() {
            long t = System.currentTimeMillis();
            if (t - lastKick < 320) return; lastKick = t;
            hitFront(85, 26 + CHAR_PWR[selected] * 2, 0.3f);
        }
        void jump() {
            if (!onGround) return;
            long t = System.currentTimeMillis();
            if (t - lastJump < 400) return; lastJump = t;
            onGround = false; jumpV = -520;
        }
        void slide() {
            float dir = facingRight ? 1 : -1; px += dir * 100; hitFront(90, 20, 0.2f);
        }
        void special() {
            long t = System.currentTimeMillis();
            if (t - lastSpecial < 1200) return; lastSpecial = t;
            hitFront(110, 40 + CHAR_PWR[selected] * 4, 0.4f); shake = 0.3f; toast("حمله ویژه!");
        }
        void shoot() {
            if (ammo <= 0) return; ammo--;
            Bullet b = new Bullet();
            b.x = px + (facingRight ? 30 : -30); b.y = py - 45;
            b.vx = facingRight ? 700 : -700; b.vy = 0; b.dmg = 22; b.life = 0.8f;
            bullets.add(b);
        }
        void hitFront(float reach, int dmg, float fxT) {
            float dir = facingRight ? 1 : -1;
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                float dx = e.x - px;
                if (dx * dir > 0 && Math.abs(dx) < reach && Math.abs(e.y - py) < 50) {
                    e.hp -= dmg; e.x += dir * 25;
                    fx.add(new Fx(e.x, e.y - 40, fxT, 1)); shake = 0.15f;
                    if (e.hp <= 0) { kills++; e.hp = 0; fx.add(new Fx(e.x, e.y - 20, 0.4f, 2)); }
                }
            }
        }
        float dist(float ax, float ay, float bx, float by) {
            float dx = ax - bx, dy = ay - by; return (float) Math.sqrt(dx * dx + dy * dy);
        }
        float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

        static class Enemy {
            float x, y, spd; int hp, max, dmg, type, weapon; long lastHit;
            void update(float dt, float tx, float ty) {
                float dx = tx - x; if (Math.abs(dx) < 2) return;
                x += Math.signum(dx) * spd * dt; y = 520;
            }
        }
        static class Bullet { float x, y, vx, vy, life; int dmg; }
        static class Fx {
            float x, y, t; int kind;
            Fx(float x, float y, float t, int k) { this.x = x; this.y = y; this.t = t; kind = k; }
        }
    }
}
