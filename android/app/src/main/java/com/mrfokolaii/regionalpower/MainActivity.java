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
        int php = 100, maxHp = 100, ammo = 0, kills, need, phase, weapon;
        float objX, objY;
        String city = "", msg = "", story = "";
        long msgUntil, lastPunch, lastKick, lastJump, lastSpecial, autoNextAt;
        boolean win, facingRight = true, onGround = true, shielding;
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        ArrayList<Fx> fx = new ArrayList<>();
        float sCX, sCY, sR = 95, kX, kY, mX;
        int stickId = -1;
        RectF bPunch, bKick, bJump, bSlide, bSpecial, bShield;

        static final String[] NAMES = {"آرش", "سارا", "رستم", "کاوه"};
        static final String[] ROLES = {"متوازن", "سریع", "قدرت", "چابک"};
        static final String[] STORY_LINE = {
            "سرباز ویژه — ضربات متعادل.",
            "عملیات مخفی — سریع و دقیق.",
            "خط مقدم — سنگین‌ترین مشت‌ها.",
            "پیشاهنگ — سرخوردن و پرش قوی."
        };
        static final int[] COL = {
            Color.rgb(70, 150, 220), Color.rgb(220, 70, 90),
            Color.rgb(220, 170, 50), Color.rgb(90, 200, 120)
        };
        static final int[] PWR = {3, 2, 5, 3};
        static final int[] SPD = {4, 5, 2, 5};
        static final int[] HP_BONUS = {0, -10, 25, -5};

        GameView(android.content.Context c) {
            super(c);
            prefs = c.getSharedPreferences("rp", 0);
            unlocked = prefs.getInt("level", 1);
            xp = prefs.getInt("xp", 0);
            selected = Math.min(3, prefs.getInt("char", 0));
            preview = unlocked;
            stages = loadStages(c);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            setKeepScreenOn(true);
        }

        static JSONArray loadStages(android.content.Context c) {
            JSONArray all = new JSONArray();
            for (int part = 1; part <= 4; part++) {
                try {
                    InputStream is = c.getAssets().open("stages_part" + part + ".json");
                    byte[] buf = new byte[is.available()]; is.read(buf); is.close();
                    JSONArray arr = new JSONArray(new String(buf, "UTF-8"));
                    for (int i = 0; i < arr.length(); i++) all.put(arr.get(i));
                } catch (Exception ignored) {}
            }
            if (all.length() == 0) {
                for (int i = 1; i <= 100; i++) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("n", i); o.put("fa", "شهر " + i);
                        o.put("enemies", 2 + i / 12); o.put("xp", 80 + i * 6);
                        o.put("brief", "پاک‌سازی منطقه"); all.put(o);
                    } catch (Exception ignored) {}
                }
            }
            return all;
        }

        protected void onSizeChanged(int w, int h, int ow, int oh) {
            W = w; H = h;
            sCX = 120; sCY = H - 120; kX = sCX; kY = sCY;
            float rx = W - 125, ry = H - 135;
            bPunch = oval(rx + 8, ry + 8, 50);
            bKick = oval(rx - 72, ry + 22, 42);
            bJump = oval(rx - 18, ry - 72, 40);
            bSlide = oval(rx - 98, ry - 38, 38);
            bSpecial = oval(rx + 32, ry - 78, 38);
            bShield = oval(rx - 52, ry - 98, 34);
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
            if (mode == 3 && win && autoNextAt > 0 && System.currentTimeMillis() > autoNextAt) {
                autoNextAt = 0;
                if (preview < 100 && preview + 1 <= unlocked) startStage(preview + 1);
                else mode = 0;
                return;
            }
            if (mode != 2) return;
            float spd = 195 + SPD[selected] * 20;
            px = clamp(px + mX * spd * dt, 60, 2200);
            if (mX > 0.12f) facingRight = true;
            if (mX < -0.12f) facingRight = false;
            if (!onGround) {
                jumpV += 1450 * dt; py += jumpV * dt;
                if (py >= 520) { py = 520; jumpV = 0; onGround = true; }
            } else py = 520;
            camX += (px - W * 0.4f - camX) * Math.min(1, dt * 5);
            camY = 60;
            for (int i = bullets.size() - 1; i >= 0; i--) {
                Bullet b = bullets.get(i);
                b.x += b.vx * dt; b.life -= dt;
                boolean hit = false;
                for (Enemy e : enemies) {
                    if (e.hp > 0 && Math.abs(b.x - e.x) < 28 && Math.abs(b.y - (e.y - 40)) < 40) {
                        e.hp -= b.dmg; hit = true;
                        fx.add(new Fx(e.x, e.y - 40, 0.22f, 1));
                        if (e.hp <= 0) { kills++; e.hp = 0; fx.add(new Fx(e.x, e.y - 20, 0.4f, 2)); }
                        break;
                    }
                }
                if (hit || b.life <= 0) bullets.remove(i);
            }
            if (phase == 0 && Math.abs(px - objX) < 50) {
                phase = 1; spawn(); toast("همه دشمنان را شکست بده!");
            } else if (phase == 1) {
                for (Enemy e : enemies) {
                    if (e.hp <= 0) continue;
                    e.update(dt, px);
                    if (!shielding && Math.abs(e.x - px) < 40 && Math.abs(e.y - py) < 30
                            && System.currentTimeMillis() - e.lastHit > 600) {
                        e.lastHit = System.currentTimeMillis();
                        php = Math.max(0, php - e.dmg);
                        shake = 0.28f;
                        fx.add(new Fx(px, py - 50, 0.2f, 1));
                        if (php <= 0) { mode = 3; win = false; autoNextAt = 0; }
                    }
                }
                for (int i = enemies.size() - 1; i >= 0; i--)
                    if (enemies.get(i).hp <= 0) enemies.remove(i);
                if (kills >= need && enemies.isEmpty()) {
                    phase = 2; mode = 3; win = true;
                    try {
                        int gain = stages.getJSONObject(preview - 1).optInt("xp", 100);
                        xp += gain;
                        if (preview >= unlocked) unlocked = Math.min(100, preview + 1);
                        prefs.edit().putInt("level", unlocked).putInt("xp", xp).putInt("char", selected).apply();
                    } catch (Exception e) {}
                    autoNextAt = System.currentTimeMillis() + 2500;
                    toast("پیروزی! مرحله بعد...");
                }
            }
            shielding = false;
        }

        void spawn() {
            enemies.clear();
            for (int i = 0; i < need; i++) {
                Enemy e = new Enemy();
                e.x = objX + 80 + i * 55 + rng.nextFloat() * 40;
                e.y = 520;
                e.hp = e.max = 35 + preview * 3;
                e.spd = 65 + preview * 1.4f;
                e.dmg = 7 + preview / 10;
                e.type = i % 3;
                e.weapon = preview >= 40 ? 2 : (preview >= 15 ? 1 : 0);
                enemies.add(e);
            }
        }

        void startStage(int n) {
            preview = n;
            try {
                JSONObject s = stages.getJSONObject(n - 1);
                city = s.optString("fa", "شهر");
                need = Math.max(2, s.optInt("enemies", 3));
                story = s.optString("brief", "منطقه را پاک‌سازی کن.");
            } catch (Exception e) {
                city = "مرحله " + n; need = 3; story = "پاک‌سازی";
            }
            weapon = n >= 40 ? 2 : (n >= 15 ? 1 : 0);
            ammo = weapon == 2 ? 18 : 0;
            maxHp = 100 + HP_BONUS[selected];
            php = maxHp;
            kills = 0; phase = 0;
            px = 160; py = 520; objX = 900;
            enemies.clear(); bullets.clear(); fx.clear();
            onGround = true; jumpV = 0; autoNextAt = 0;
            mode = 2;
            toast(city + " — " + story);
        }

        void toast(String m) { msg = m; msgUntil = System.currentTimeMillis() + 3000; }

        protected void onDraw(Canvas c) {
            if (W == 0) return;
            if (mode == 0) drawMenu(c);
            else if (mode == 1) drawCharSelect(c);
            else if (mode == 2) drawPlay(c);
            else drawResult(c);
        }

        void drawMenu(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(8, 14, 24), Color.rgb(4, 6, 10), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            for (int i = 0; i < 14; i++) {
                p.setColor(Color.rgb(14, 20, 32));
                float bx = i * (W / 13f);
                c.drawRect(bx, H * 0.4f + (i % 4) * 25, bx + W / 14f, H, p);
            }
            p.setColor(Color.argb(210, 0, 0, 0));
            c.drawRoundRect(36, 36, W - 36, H - 36, 18, 18, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(Color.rgb(212, 175, 90));
            c.drawRoundRect(36, 36, W - 36, H - 36, 18, 18, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(48); text.setColor(Color.rgb(220, 185, 80));
            c.drawText("Regional Power", 64, 100, text);
            text.setTextSize(22); text.setColor(Color.rgb(170, 185, 200));
            c.drawText("داستان پاک‌سازی ۱۰۰ شهر ایران", 64, 138, text);
            c.drawText("پیشرفت " + unlocked + "/100    XP " + xp, 64, 172, text);
            drawFighter(c, W - 160, H * 0.55f, COL[selected], true, 2.2f, false, selected);
            text.setTextSize(26); text.setColor(COL[selected]);
            c.drawText(NAMES[selected], W - 220, H * 0.55f - 130, text);
            text.setTextSize(18); text.setColor(Color.LTGRAY);
            c.drawText(ROLES[selected], W - 220, H * 0.55f - 105, text);
            String name = preview + " — ";
            try { name += stages.getJSONObject(preview - 1).optString("fa"); } catch (Exception e) { name += "شهر"; }
            boolean lock = preview > unlocked;
            text.setTextSize(32); text.setColor(lock ? Color.GRAY : Color.WHITE);
            c.drawText(name + (lock ? "  [قفل]" : ""), 64, 240, text);
            try {
                text.setTextSize(18); text.setColor(Color.rgb(180, 190, 200));
                c.drawText(stages.getJSONObject(preview - 1).optString("brief"), 64, 278, text);
            } catch (Exception e) {}
            drawBtn(c, W * 0.05f, H - 120, 130, 52, "قبلی");
            drawBtn(c, W * 0.25f, H - 120, 150, 52, "شخصیت");
            drawBtn(c, W * 0.48f, H - 120, 160, 52, lock ? "قفل" : "شروع");
            drawBtn(c, W * 0.72f, H - 120, 130, 52, "بعدی");
        }

        void drawCharSelect(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(10, 16, 30), Color.rgb(4, 6, 12), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            text.setTextSize(40); text.setColor(Color.rgb(220, 185, 80)); text.setTextAlign(Paint.Align.CENTER);
            c.drawText("انتخاب مبارز", W / 2f, 60, text); text.setTextAlign(Paint.Align.LEFT);
            float cellW = (W - 60) / 4f;
            for (int i = 0; i < 4; i++) {
                float x = 30 + i * cellW;
                boolean sel = i == selected;
                p.setColor(sel ? Color.rgb(40, 55, 35) : Color.rgb(18, 24, 36));
                c.drawRoundRect(x, 90, x + cellW - 14, H - 120, 14, 14, p);
                if (sel) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(COL[i]);
                    c.drawRoundRect(x, 90, x + cellW - 14, H - 120, 14, 14, p); p.setStyle(Paint.Style.FILL);
                }
                drawFighter(c, x + cellW / 2 - 7, H * 0.52f, COL[i], true, 1.8f, false, i);
                text.setTextSize(24); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
                c.drawText(NAMES[i], x + cellW / 2 - 7, H - 160, text);
                text.setTextSize(16); text.setColor(COL[i]);
                c.drawText(ROLES[i], x + cellW / 2 - 7, H - 138, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
            text.setTextSize(18); text.setColor(Color.LTGRAY);
            c.drawText(STORY_LINE[selected], 40, H - 85, text);
            text.setTextSize(20); text.setColor(Color.WHITE);
            c.drawText("قدرت " + stars(PWR[selected]) + "  سرعت " + stars(SPD[selected]) + "  جان " + (100 + HP_BONUS[selected]), 40, H - 55, text);
            drawBtn(c, W / 2f - 90, H - 48, 180, 40, "تأیید");
        }

        String stars(int n) {
            String s = ""; for (int i = 0; i < 5; i++) s += i < n ? "★" : "☆"; return s;
        }

        void drawPlay(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, H, Color.rgb(16, 26, 42), Color.rgb(10, 12, 18), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p); p.setShader(null);
            c.save();
            float sx = shake > 0 ? (rng.nextFloat() - 0.5f) * 14 * shake : 0;
            c.translate(-camX + sx, -camY);
            p.setColor(Color.rgb(32, 36, 44));
            c.drawRect(camX - 80, 540, camX + W + 80, 720, p);
            p.setColor(Color.rgb(50, 54, 62));
            for (int i = 0; i < 28; i++) c.drawRect(i * 95f, 552, i * 95f + 55, 560, p);
            for (int i = 0; i < 18; i++) {
                float bx = i * 150f;
                int bh = 160 + (i * 41) % 140;
                p.setColor(Color.rgb(18 + (i % 4) * 5, 24, 36));
                c.drawRect(bx, 540 - bh, bx + 110, 540, p);
                p.setColor(Color.argb(90, 255, 210, 90));
                for (int wy = 0; wy < 5; wy++)
                    for (int wx = 0; wx < 2; wx++)
                        c.drawRect(bx + 18 + wx * 40, 540 - bh + 18 + wy * 28, bx + 36 + wx * 40, 540 - bh + 32 + wy * 28, p);
            }
            if (phase == 0) {
                p.setColor(Color.argb(70, 255, 200, 40));
                c.drawCircle(objX, 520, 45, p);
                text.setTextSize(18); text.setColor(Color.rgb(255, 210, 60)); text.setTextAlign(Paint.Align.CENTER);
                c.drawText("ورود به منطقه", objX, 460, text); text.setTextAlign(Paint.Align.LEFT);
            }
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                int col = e.type == 1 ? Color.rgb(150, 45, 45) : e.type == 2 ? Color.rgb(45, 70, 150) : Color.rgb(110, 85, 45);
                drawFighter(c, e.x, e.y, col, e.x < px, 1.05f, e.weapon > 0, -1);
                float r = e.hp / (float) e.max;
                p.setColor(Color.argb(180, 0, 0, 0));
                c.drawRect(e.x - 26, e.y - 100, e.x + 26, e.y - 92, p);
                p.setColor(r > 0.35f ? Color.rgb(70, 200, 100) : Color.rgb(220, 50, 50));
                c.drawRect(e.x - 26, e.y - 100, e.x - 26 + 52 * r, e.y - 92, p);
            }
            drawFighter(c, px, py, COL[selected], facingRight, 1.25f, weapon > 0, selected);
            for (Bullet b : bullets) {
                p.setColor(Color.rgb(255, 230, 90));
                c.drawCircle(b.x, b.y, 5, p);
            }
            for (Fx f : fx) {
                int a = (int) (220 * Math.max(0, f.t / 0.45f));
                p.setColor(Color.argb(a, 255, f.kind == 2 ? 70 : 140, 40));
                c.drawCircle(f.x, f.y, 12 + (0.45f - f.t) * 36, p);
            }
            c.restore();
            p.setColor(Color.argb(230, 4, 6, 10));
            c.drawRect(0, 0, W, 70, p);
            text.setTextSize(16); text.setColor(Color.WHITE);
            c.drawText("جان", 14, 26, text);
            p.setColor(Color.rgb(35, 35, 40));
            c.drawRoundRect(52, 12, 232, 30, 5, 5, p);
            float hpW = 180f * php / Math.max(1, maxHp);
            p.setColor(php > maxHp * 0.35f ? Color.rgb(60, 200, 110) : Color.rgb(220, 50, 50));
            c.drawRoundRect(52, 12, 52 + hpW, 30, 5, 5, p);
            text.setTextSize(14); text.setColor(Color.WHITE);
            c.drawText(php + "/" + maxHp, 58, 26, text);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(17); text.setColor(Color.rgb(200, 210, 220));
            String wpn = weapon == 2 ? "اسلحه×" + ammo : weapon == 1 ? "چاقو" : "دست‌خالی";
            c.drawText(city + "  |  دشمن " + kills + "/" + need + "  |  " + wpn, W / 2f, 26, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(Color.rgb(220, 185, 80));
            c.drawText("XP " + xp, W - 100, 26, text);
            text.setTextSize(15); text.setColor(COL[selected]);
            c.drawText(NAMES[selected], 14, 52, text);
            text.setTextSize(13); text.setColor(Color.GRAY);
            c.drawText(story, 100, 52, text);
            if (System.currentTimeMillis() < msgUntil) {
                p.setColor(Color.argb(190, 0, 0, 0));
                c.drawRoundRect(W / 2f - 220, H * 0.12f - 22, W / 2f + 220, H * 0.12f + 22, 10, 10, p);
                text.setTextAlign(Paint.Align.CENTER); text.setTextSize(22); text.setColor(Color.WHITE);
                c.drawText(msg, W / 2f, H * 0.12f + 7, text); text.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(Color.argb(45, 255, 255, 255)); c.drawCircle(sCX, sCY, sR, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(130, 255, 255, 255));
            c.drawCircle(sCX, sCY, sR, p); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(230, 245, 245, 250)); c.drawCircle(kX, kY, 32, p);
            drawAct(c, bPunch, "مشت", Color.rgb(220, 90, 60));
            drawAct(c, bKick, "لگد", Color.rgb(200, 140, 50));
            drawAct(c, bJump, "پرش", Color.rgb(80, 150, 220));
            drawAct(c, bSlide, "سر", Color.rgb(80, 190, 160));
            drawAct(c, bSpecial, "ویژه", Color.rgb(200, 70, 180));
            drawAct(c, bShield, "سپر", Color.rgb(120, 140, 190));
        }

        void drawFighter(Canvas c, float x, float y, int col, boolean faceRight, float sc, boolean armed, int style) {
            float s = sc, dir = faceRight ? 1f : -1f;
            p.setColor(Color.argb(55, 0, 0, 0));
            c.drawOval(x - 24 * s, y + 2, x + 24 * s, y + 14, p);
            p.setColor(Color.rgb(30, 30, 35));
            c.drawRoundRect(x - 14 * s, y - 8 * s, x - 2 * s, y + 4, 3, 3, p);
            c.drawRoundRect(x + 2 * s, y - 8 * s, x + 14 * s, y + 4, 3, 3, p);
            int leg = Color.rgb(Math.max(0, Color.red(col) * 2 / 3), Math.max(0, Color.green(col) * 2 / 3), Math.max(0, Color.blue(col) * 2 / 3));
            p.setColor(leg);
            c.drawRoundRect(x - 11 * s, y - 32 * s, x - 2 * s, y - 6 * s, 4, 4, p);
            c.drawRoundRect(x + 2 * s, y - 32 * s, x + 11 * s, y - 6 * s, 4, 4, p);
            p.setColor(col);
            c.drawRoundRect(x - 16 * s, y - 62 * s, x + 16 * s, y - 28 * s, 7, 7, p);
            p.setColor(Color.rgb(40, 40, 45));
            c.drawRect(x - 16 * s, y - 34 * s, x + 16 * s, y - 28 * s, p);
            p.setColor(Color.rgb(215, 175, 145));
            c.drawCircle(x, y - 76 * s, 14 * s, p);
            if (style == 0) {
                p.setColor(Color.rgb(40, 90, 180));
                c.drawArc(x - 14 * s, y - 90 * s, x + 14 * s, y - 68 * s, 200, 140, true, p);
            } else if (style == 1) {
                p.setColor(Color.rgb(90, 50, 30));
                c.drawOval(x - 16 * s, y - 92 * s, x + 16 * s, y - 70 * s, p);
                c.drawOval(x + 8 * s * dir, y - 80 * s, x + 20 * s * dir, y - 50 * s, p);
            } else if (style == 2) {
                p.setColor(Color.rgb(25, 25, 28));
                c.drawArc(x - 14 * s, y - 90 * s, x + 14 * s, y - 68 * s, 200, 140, true, p);
            } else if (style == 3) {
                p.setColor(Color.rgb(200, 40, 40));
                c.drawArc(x - 15 * s, y - 92 * s, x + 15 * s, y - 70 * s, 200, 160, true, p);
                c.drawRect(x + 5 * s * dir, y - 80 * s, x + 22 * s * dir, y - 74 * s, p);
            } else {
                p.setColor(Color.rgb(30, 30, 35));
                c.drawArc(x - 13 * s, y - 88 * s, x + 13 * s, y - 68 * s, 200, 140, true, p);
            }
            p.setColor(col);
            c.drawRoundRect(x - 24 * s * dir - 5, y - 58 * s, x - 24 * s * dir + 7, y - 30 * s, 5, 5, p);
            c.drawRoundRect(x + 10 * s * dir - 5, y - 56 * s, x + 10 * s * dir + 8, y - 28 * s, 5, 5, p);
            p.setColor(Color.rgb(230, 190, 160));
            c.drawCircle(x + 26 * s * dir, y - 34 * s, 8 * s, p);
            if (armed) {
                p.setColor(Color.rgb(190, 195, 200));
                c.drawRect(x + 20 * s * dir, y - 52 * s, x + 40 * s * dir, y - 47 * s, p);
            }
        }

        void drawAct(Canvas c, RectF r, String lab, int col) {
            p.setColor(Color.argb(145, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawOval(r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(210, 255, 255, 255));
            c.drawOval(r, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(12); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, r.centerX(), r.centerY() + 4, text); text.setTextAlign(Paint.Align.LEFT);
        }

        void drawResult(Canvas c) {
            p.setColor(Color.rgb(8, 12, 18));
            c.drawRect(0, 0, W, H, p);
            p.setColor(Color.argb(220, 0, 0, 0));
            c.drawRoundRect(W / 2f - 220, H / 2f - 120, W / 2f + 220, H / 2f + 130, 16, 16, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(48); text.setColor(win ? Color.rgb(70, 210, 120) : Color.rgb(220, 55, 55));
            c.drawText(win ? "پیروزی" : "شکست", W / 2f, H / 2f - 50, text);
            text.setTextSize(22); text.setColor(Color.WHITE);
            c.drawText(win ? city + " پاک شد" : "جانت تمام شد", W / 2f, H / 2f - 5, text);
            if (win && autoNextAt > 0) {
                text.setTextSize(18); text.setColor(Color.rgb(220, 185, 80));
                c.drawText("مرحله بعد به‌زودی...", W / 2f, H / 2f + 30, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
            drawBtn(c, W / 2f - 100, H / 2f + 55, 200, 48, "منو");
        }

        void drawBtn(Canvas c, float x, float y, float w, float h, String lab) {
            p.setColor(Color.rgb(28, 50, 42));
            c.drawRoundRect(x, y, x + w, y + h, 12, 12, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.rgb(212, 175, 90));
            c.drawRoundRect(x, y, x + w, y + h, 12, 12, p); p.setStyle(Paint.Style.FILL);
            text.setTextSize(20); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x + w / 2, y + h / 2 + 7, text); text.setTextAlign(Paint.Align.LEFT);
        }

        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked(), idx = e.getActionIndex();
            float x = e.getX(idx), y = e.getY(idx); int id = e.getPointerId(idx);
            if (mode == 0) {
                if (action == MotionEvent.ACTION_UP && y > H - 120 && y < H - 68) {
                    if (x > W * 0.05f && x < W * 0.05f + 130) preview = Math.max(1, preview - 1);
                    else if (x > W * 0.25f && x < W * 0.25f + 150) mode = 1;
                    else if (x > W * 0.48f && x < W * 0.48f + 160) {
                        if (preview <= unlocked) startStage(preview);
                    } else if (x > W * 0.72f && x < W * 0.72f + 130) preview = Math.min(100, preview + 1);
                }
                return true;
            }
            if (mode == 1) {
                if (action == MotionEvent.ACTION_UP) {
                    float cellW = (W - 60) / 4f;
                    for (int i = 0; i < 4; i++) {
                        float cx = 30 + i * cellW;
                        if (x > cx && x < cx + cellW - 14 && y > 90 && y < H - 120) selected = i;
                    }
                    if (y > H - 48 && y < H - 8 && x > W / 2f - 90 && x < W / 2f + 90) {
                        prefs.edit().putInt("char", selected).apply(); mode = 0;
                    }
                }
                return true;
            }
            if (mode == 3) {
                if (action == MotionEvent.ACTION_UP && y > H / 2f + 55 && y < H / 2f + 103) {
                    autoNextAt = 0; mode = 0;
                }
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
                    if (id == stickId) { stickId = -1; mX = 0; kX = sCX; kY = sCY; }
                    break;
            }
            return true;
        }

        void moveStick(float x, float y) {
            float dx = x - sCX, dy = y - sCY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > sR) { dx = dx / len * sR; dy = dy / len * sR; }
            kX = sCX + dx; kY = sCY + dy; mX = dx / sR;
        }
        void press(float x, float y) {
            if (bPunch.contains(x, y)) punch();
            else if (bKick.contains(x, y)) kick();
            else if (bJump.contains(x, y)) jump();
            else if (bSlide.contains(x, y)) slide();
            else if (bSpecial.contains(x, y)) special();
            else if (bShield.contains(x, y)) shielding = true;
        }
        void punch() {
            long t = System.currentTimeMillis();
            if (t - lastPunch < 200) return; lastPunch = t;
            if (weapon == 2 && ammo > 0) { shoot(); return; }
            hitFront(72, 16 + PWR[selected] * 4 + (weapon == 1 ? 10 : 0), 0.22f);
        }
        void kick() {
            long t = System.currentTimeMillis();
            if (t - lastKick < 300) return; lastKick = t;
            hitFront(88, 24 + PWR[selected] * 3, 0.28f);
        }
        void jump() {
            if (!onGround) return;
            long t = System.currentTimeMillis();
            if (t - lastJump < 380) return; lastJump = t;
            onGround = false; jumpV = -530;
        }
        void slide() {
            px += (facingRight ? 1 : -1) * (90 + SPD[selected] * 8);
            hitFront(95, 18, 0.2f);
        }
        void special() {
            long t = System.currentTimeMillis();
            if (t - lastSpecial < 1100) return; lastSpecial = t;
            hitFront(120, 38 + PWR[selected] * 5, 0.4f);
            shake = 0.32f; toast("حمله ویژه!");
        }
        void shoot() {
            if (ammo <= 0) return; ammo--;
            Bullet b = new Bullet();
            b.x = px + (facingRight ? 35 : -35); b.y = py - 50;
            b.vx = facingRight ? 720 : -720; b.dmg = 20; b.life = 0.75f;
            bullets.add(b);
        }
        void hitFront(float reach, int dmg, float t) {
            float dir = facingRight ? 1 : -1;
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                float dx = e.x - px;
                if (dx * dir > 0 && Math.abs(dx) < reach && Math.abs(e.y - py) < 45) {
                    e.hp -= dmg; e.x += dir * 28;
                    fx.add(new Fx(e.x, e.y - 45, t, 1)); shake = 0.14f;
                    if (e.hp <= 0) { kills++; e.hp = 0; fx.add(new Fx(e.x, e.y - 25, 0.4f, 2)); }
                }
            }
        }
        float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

        static class Enemy {
            float x, y, spd; int hp, max, dmg, type, weapon; long lastHit;
            void update(float dt, float tx) {
                float dx = tx - x; if (Math.abs(dx) < 3) return;
                x += Math.signum(dx) * spd * dt; y = 520;
            }
        }
        static class Bullet { float x, y, vx, life; int dmg; }
        static class Fx {
            float x, y, t; int kind;
            Fx(float x, float y, float t, int k) { this.x = x; this.y = y; this.t = t; kind = k; }
        }
    }
}
