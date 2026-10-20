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
        float px, py, pAngle, camX, camY;
        int php = 100, ammo = 30, kills, need, phase;
        float objX, objY, extX, extY;
        String city = "", msg = "";
        long msgUntil;
        boolean win;
        ArrayList<Enemy> enemies = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        float sCX, sCY, sR = 100, kX, kY, mX, mY;
        int stickId = -1;
        RectF bFire, bMelee, bSlide, bReload, bJump, bSpecial;
        long lastFire, lastMelee;
        final int C_GOLD = Color.rgb(212, 175, 90);
        final int C_GREEN = Color.rgb(60, 160, 100);
        final int C_RED = Color.rgb(200, 60, 55);

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
            String[] files = {
                "stages_100.json",
                "stages_part1.json", "stages_part2.json",
                "stages_part3.json", "stages_part4.json"
            };
            try {
                InputStream is = c.getAssets().open("stages_100.json");
                byte[] buf = new byte[is.available()];
                is.read(buf); is.close();
                JSONArray one = new JSONArray(new String(buf, "UTF-8"));
                if (one.length() >= 50) return one;
            } catch (Exception ignored) {}
            for (int p = 1; p <= 4; p++) {
                try {
                    InputStream is = c.getAssets().open("stages_part" + p + ".json");
                    byte[] buf = new byte[is.available()];
                    is.read(buf); is.close();
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
            bJump = oval(rx - 70, ry - 130, 52);
            bReload = oval(rx + 30, ry - 120, 48);
            bSpecial = oval(rx - 40, ry - 40, 55);
        }
        RectF oval(float cx, float cy, float r) { return new RectF(cx - r, cy - r, cx + r, cy + r); }

        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            running = true;
            thread = new Thread(this);
            thread.start();
        }
        public void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

        public void run() {
            long last = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float dt = Math.min(0.033f, (now - last) / 1e9f);
                last = now;
                update(dt);
                postInvalidate();
                try { Thread.sleep(12); } catch (Exception e) {}
            }
        }

        void update(float dt) {
            if (mode != 1) return;
            px = clamp(px + mX * 260 * dt, 80, 2000);
            py = clamp(py + mY * 260 * dt, 80, 1200);
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
                        if (e.hp <= 0) { kills++; e.hp = 0; }
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
                        e.lastHit = System.currentTimeMillis();
                        php -= e.dmg;
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
            enemies.clear(); bullets.clear();
            mode = 1;
        }

        void toast(String m) { msg = m; msgUntil = System.currentTimeMillis() + 3200; }

        protected void onDraw(Canvas c) {
            if (W == 0) return;
            c.drawColor(Color.rgb(18, 28, 24));
            if (mode == 0) drawMenu(c);
            else if (mode == 1) drawPlay(c);
            else drawResult(c);
        }

        void drawMenu(Canvas c) {
            text.setTextSize(48); text.setColor(C_GOLD);
            c.drawText("Regional Power", 60, 90, text);
            text.setTextSize(26); text.setColor(Color.WHITE);
            c.drawText("پاک سازی ۱۰۰ شهر ایران", 60, 130, text);
            c.drawText("پیشرفت " + unlocked + "/100  XP " + xp, 60, 170, text);
            String name = "مرحله " + preview;
            try { name = preview + " — " + stages.getJSONObject(preview - 1).optString("fa"); } catch (Exception e) {}
            boolean lock = preview > unlocked;
            text.setTextSize(34); text.setColor(lock ? Color.GRAY : Color.WHITE);
            c.drawText(name + (lock ? " [قفل]" : ""), 60, 240, text);
            drawBtn(c, W * 0.08f, H - 140, 160, 64, "قبلی");
            drawBtn(c, W * 0.35f, H - 140, 220, 64, lock ? "قفل" : "شروع");
            drawBtn(c, W * 0.68f, H - 140, 160, 64, "بعدی");
        }

        void drawPlay(Canvas c) {
            c.save();
            c.translate(-camX, -camY);
            if (phase == 0) { p.setColor(Color.argb(80, 255, 200, 40)); c.drawCircle(objX, objY, 48, p); }
            if (phase == 2) { p.setColor(Color.argb(80, 40, 220, 120)); c.drawCircle(extX, extY, 48, p); }
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                p.setColor(e.type == 1 ? C_RED : e.type == 2 ? Color.rgb(70, 100, 200) : Color.rgb(150, 100, 60));
                c.drawCircle(e.x, e.y, 24, p);
            }
            p.setColor(C_GREEN); c.drawCircle(px, py, 26, p);
            p.setColor(Color.rgb(255, 230, 120));
            for (Bullet b : bullets) c.drawCircle(b.x, b.y, 4, p);
            c.restore();
            text.setTextSize(24); text.setColor(Color.WHITE);
            c.drawText("HP " + php + "  " + city + "  " + kills + "/" + need, 20, 40, text);
            p.setColor(Color.argb(55, 255, 255, 255)); c.drawCircle(sCX, sCY, sR, p);
            p.setColor(Color.argb(200, 230, 230, 230)); c.drawCircle(kX, kY, 36, p);
            drawAct(c, bFire, "شلیک"); drawAct(c, bMelee, "مشت");
            drawAct(c, bSlide, "اسلاید"); drawAct(c, bReload, "گلوله");
            if (System.currentTimeMillis() < msgUntil) {
                text.setTextAlign(Paint.Align.CENTER); c.drawText(msg, W/2f, H*0.2f, text); text.setTextAlign(Paint.Align.LEFT);
            }
        }

        void drawResult(Canvas c) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(48); text.setColor(win ? C_GREEN : C_RED);
            c.drawText(win ? "پیروزی" : "شکست", W/2f, H/2f - 40, text);
            text.setTextAlign(Paint.Align.LEFT);
            drawBtn(c, W/2f - 110, H/2f + 50, 220, 56, "منو");
        }

        void drawAct(Canvas c, RectF r, String lab) {
            p.setColor(Color.argb(120, 80, 120, 100)); c.drawOval(r, p);
            text.setTextSize(16); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, r.centerX(), r.centerY() + 5, text); text.setTextAlign(Paint.Align.LEFT);
        }
        void drawBtn(Canvas c, float x, float y, float w, float h, String lab) {
            p.setColor(Color.rgb(40, 70, 55)); c.drawRoundRect(x, y, x+w, y+h, 14, 14, p);
            text.setTextSize(26); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
            c.drawText(lab, x+w/2, y+h/2+9, text); text.setTextAlign(Paint.Align.LEFT);
        }

        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked(), idx = e.getActionIndex();
            float x = e.getX(idx), y = e.getY(idx); int id = e.getPointerId(idx);
            if (mode == 0) {
                if (action == MotionEvent.ACTION_UP && y > H - 140 && y < H - 76) {
                    if (x > W*0.08f && x < W*0.08f+160) preview = Math.max(1, preview-1);
                    else if (x > W*0.35f && x < W*0.35f+220) { if (preview <= unlocked) startStage(preview); }
                    else if (x > W*0.68f && x < W*0.68f+160) preview = Math.min(100, preview+1);
                }
                return true;
            }
            if (mode == 2) {
                if (action == MotionEvent.ACTION_UP && y > H/2f+50 && y < H/2f+110) mode = 0;
                return true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (x < W*0.42f) { stickId = id; moveStick(x,y); } else press(x,y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    for (int i=0;i<e.getPointerCount();i++)
                        if (e.getPointerId(i)==stickId) moveStick(e.getX(i), e.getY(i));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (id==stickId) { stickId=-1; mX=mY=0; kX=sCX; kY=sCY; }
                    break;
            }
            return true;
        }

        void moveStick(float x, float y) {
            float dx=x-sCX, dy=y-sCY;
            float len=(float)Math.sqrt(dx*dx+dy*dy);
            if (len>sR) { dx=dx/len*sR; dy=dy/len*sR; }
            kX=sCX+dx; kY=sCY+dy; mX=dx/sR; mY=dy/sR;
        }
        void press(float x, float y) {
            if (bFire.contains(x,y)) fire();
            else if (bMelee.contains(x,y)||bSpecial.contains(x,y)) melee();
            else if (bReload.contains(x,y)) { ammo=30; toast("پر شد"); }
            else if (bSlide.contains(x,y)) { px+=mX*90; py+=mY*90; }
        }
        void fire() {
            long t=System.currentTimeMillis();
            if (t-lastFire<160||ammo<=0) return;
            lastFire=t; ammo--;
            Bullet b=new Bullet(); b.x=px; b.y=py;
            float a=pAngle;
            Enemy best=null; float bd=400;
            for (Enemy e:enemies) {
                if (e.hp<=0) continue;
                float d=dist(px,py,e.x,e.y);
                if (d<bd) { bd=d; best=e; }
            }
            if (best!=null) a=(float)Math.atan2(best.y-py, best.x-px);
            b.vx=(float)Math.cos(a)*620; b.vy=(float)Math.sin(a)*620;
            b.dmg=18+preview/5; b.life=0.9f; bullets.add(b);
        }
        void melee() {
            long t=System.currentTimeMillis();
            if (t-lastMelee<280) return;
            lastMelee=t;
            for (Enemy e:enemies)
                if (e.hp>0 && dist(px,py,e.x,e.y)<75) { e.hp-=30; if (e.hp<=0) kills++; }
        }
        float dist(float ax,float ay,float bx,float by) {
            float dx=ax-bx, dy=ay-by; return (float)Math.sqrt(dx*dx+dy*dy);
        }
        float clamp(float v,float a,float b){ return Math.max(a,Math.min(b,v)); }

        static class Enemy {
            float x,y,spd; int hp,max,dmg,type; long lastHit;
            void update(float dt,float tx,float ty) {
                float dx=tx-x, dy=ty-y;
                float len=(float)Math.sqrt(dx*dx+dy*dy);
                if (len<1) return;
                float prefer=type==2?170:45;
                if (type==2&&len<prefer){ dx=-dx; dy=-dy; len=(float)Math.sqrt(dx*dx+dy*dy); }
                x+=dx/len*spd*dt; y+=dy/len*spd*dt;
            }
        }
        static class Bullet { float x,y,vx,vy,life; int dmg; }
    }
}
