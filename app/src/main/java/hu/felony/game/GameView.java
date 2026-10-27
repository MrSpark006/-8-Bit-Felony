package hu.felony.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    static final int T = 64, N = 48, LH = 200, AC = 10;
    final int[] SP = {8, 6, 10};   // úttávolság térképenként
    final int[] KM = {5, 7, 4};    // utolsó kereszteződés indexe
    final int[][][] TG = {
        {{4, 1}, {4, 4}, {1, 5}},
        {{6, 2}, {2, 6}, {6, 6}},
        {{4, 1}, {4, 4}, {2, 3}}};
    final String[][] MT = {
        {"1. küldetés: Vidd el a csomagot a sárga körhöz!", "2. küldetés: Hajts a bankhoz!", "3. küldetés: Menekülj a rejtekhelyre!"},
        {"1. küldetés: Vedd fel a megbízót!", "2. küldetés: Vidd a rakományt a raktárhoz!", "3. küldetés: Hajts a kikötői hajóhoz!"},
        {"1. küldetés: Keresd meg a kapcsolatot!", "2. küldetés: Hozd el a zsákmányt!", "3. küldetés: Menekülj az országhatárig!"}};
    final String[] MN = {"Belváros", "Kikötő", "Sivatag"};
    final int[] ROAD = {0xFF4A4A52, 0xFF30303A, 0xFF6A6252};
    final int[][] BASE = {{0xFF6B4F3A, 0xFF5B5F73, 0xFF74604A}, {0xFF2F4F4F, 0xFF4B3A66, 0xFF3A5F3A}, {0xFF9C6B30, 0xFFB08850, 0xFF8A5A3C}};
    final int[][] ROOF = {{0xFF8B6B4A, 0xFF7A7F96, 0xFF9A8260}, {0xFF4F7F7F, 0xFF6B5A8F, 0xFF5A8F5A}, {0xFFC48A48, 0xFFD0A870, 0xFFB07A5C}};
    final int[] CAR = {0xFF2E7D32, 0xFF1976D2, 0xFFFBC02D, 0xFF8E24AA};
    final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    final int[] OX = {0, -16, 0, 16}, OY = {16, 0, -16, 0};

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint wp = new Paint();   // pixeles (nem simított) festék
    final Paint fp = new Paint();
    final Random rnd = new Random();
    final SharedPreferences sp;
    Thread th;
    volatile boolean running;
    volatile int state = 0; // 0 menü, 1 játék, 2 kész
    volatile Bitmap bmp;
    volatile Canvas bcv;
    final Rect dst = new Rect();
    int unlocked, W = 1, H = 1, mission, map;
    float cx, cy, ang, spd;
    boolean left, right, gas, brake;
    final boolean[][] solid = new boolean[N][N];
    final float[] ax = new float[AC], ay = new float[AC], asp = new float[AC];
    final int[] akx = new int[AC], aky = new int[AC], adir = new int[AC];
    final RectF[] mb = {new RectF(), new RectF(), new RectF()};
    final RectF bl = new RectF(), br = new RectF(), bg = new RectF(), bk = new RectF();

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        sp = ctx.getSharedPreferences("g", 0);
        unlocked = sp.getInt("u", 1);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    void startGame(int m) {
        map = m;
        int S = SP[m];
        for (int x = 0; x < N; x++)
            for (int y = 0; y < N; y++) {
                boolean road = (x % S < 2 || y % S < 2) && x > 0 && y > 0 && x < N - 1 && y < N - 1;
                solid[x][y] = !road;
            }
        mission = 0; cx = cy = (S + 1) * T; ang = 0; spd = 0;
        left = right = gas = brake = false;
        for (int i = 0; i < AC; i++) {
            akx[i] = 1 + rnd.nextInt(KM[m]);
            aky[i] = 1 + rnd.nextInt(KM[m]);
            if (akx[i] == 1 && aky[i] == 1) aky[i] = 2;
            adir[i] = rnd.nextInt(4);
            asp[i] = 80 + rnd.nextInt(60);
            ax[i] = (S * akx[i] + 1) * T + OX[adir[i]];
            ay[i] = (S * aky[i] + 1) * T + OY[adir[i]];
        }
        state = 1;
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        running = true; th = new Thread(this); th.start();
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        W = w; H = hh;
        int lw = Math.max(1, Math.round(W * (float) LH / H));
        Bitmap b = Bitmap.createBitmap(lw, LH, Bitmap.Config.ARGB_8888);
        bcv = new Canvas(b); bmp = b;
        dst.set(0, 0, W, H);
        float bs = H * 0.22f, m = H * 0.04f;
        bl.set(m, H - bs - m, m + bs, H - m);
        br.set(2 * m + bs, H - bs - m, 2 * m + 2 * bs, H - m);
        bg.set(W - m - bs, H - bs - m, W - m, H - m);
        bk.set(W - 2 * m - 2 * bs, H - bs - m, W - 2 * m - bs, H - m);
        float bw = W * 0.5f, bh = H * 0.17f;
        for (int i = 0; i < 3; i++)
            mb[i].set(W / 2f - bw / 2, H * 0.32f + i * (bh + H * 0.04f),
                      W / 2f + bw / 2, H * 0.32f + i * (bh + H * 0.04f) + bh);
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { th.join(); } catch (Exception e) { }
    }

    @Override public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            if (state == 1) update(dt);
            SurfaceHolder h = getHolder();
            if (h.getSurface().isValid()) {
                Canvas c = h.lockCanvas();
                if (c != null) {
                    try { draw(c); } finally { h.unlockCanvasAndPost(c); }
                }
            }
            try { Thread.sleep(8); } catch (Exception e) { }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (state != 1) {
            if (a == MotionEvent.ACTION_DOWN) {
                if (state == 2) state = 0;
                else for (int i = 0; i < 3; i++)
                    if (i < unlocked && mb[i].contains(e.getX(), e.getY())) startGame(i);
            }
            return true;
        }
        left = right = gas = brake = false;
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) return true;
        int skip = a == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            float x = e.getX(i), y = e.getY(i);
            if (bl.contains(x, y)) left = true;
            if (br.contains(x, y)) right = true;
            if (bg.contains(x, y)) gas = true;
            if (bk.contains(x, y)) brake = true;
        }
        return true;
    }

    boolean hit(float x, float y) {
        float r = 14;
        int x0 = (int) Math.floor((x - r) / T), x1 = (int) Math.floor((x + r) / T);
        int y0 = (int) Math.floor((y - r) / T), y1 = (int) Math.floor((y + r) / T);
        for (int i = x0; i <= x1; i++)
            for (int j = y0; j <= y1; j++)
                if (i < 0 || j < 0 || i >= N || j >= N || solid[i][j]) return true;
        return false;
    }

    void aiStep(int i, float dt) {
        int d = adir[i], S = SP[map];
        float tx = (S * akx[i] + 1) * T + OX[d], ty = (S * aky[i] + 1) * T + OY[d];
        float dx = tx - ax[i], dy = ty - ay[i];
        float dist = (float) Math.hypot(dx, dy), st = asp[i] * dt;
        if (dist <= st) {
            ax[i] = tx; ay[i] = ty;
            for (int t = 0; t < 30; t++) {
                int nd = rnd.nextInt(4);
                int nx = akx[i] + DX[nd], ny = aky[i] + DY[nd];
                if (nd != (d + 2) % 4 && nx >= 1 && nx <= KM[map] && ny >= 1 && ny <= KM[map]) {
                    akx[i] = nx; aky[i] = ny; adir[i] = nd;
                    break;
                }
            }
        } else {
            ax[i] += dx / dist * st; ay[i] += dy / dist * st;
        }
    }

    void update(float dt) {
        if (gas) spd += 280 * dt;
        if (brake) spd -= 380 * dt;
        if (!gas && !brake) spd -= spd * 1.6f * dt;
        spd = Math.max(-120, Math.min(340, spd));
        float st = (right ? 1 : 0) - (left ? 1 : 0);
        ang += st * 2.8f * dt * Math.min(1f, Math.abs(spd) / 90f) * (spd >= 0 ? 1 : -1);
        float nx = cx + (float) Math.cos(ang) * spd * dt;
        float ny = cy + (float) Math.sin(ang) * spd * dt;
        boolean c = false;
        if (!hit(nx, cy)) cx = nx; else c = true;
        if (!hit(cx, ny)) cy = ny; else c = true;
        if (c) spd *= -0.3f;
        for (int i = 0; i < AC; i++) {
            aiStep(i, dt);
            float dx = cx - ax[i], dy = cy - ay[i];
            float d = (float) Math.hypot(dx, dy);
            if (d < 28 && d > 0.01f) {
                float px = cx + dx / d * 6, py = cy + dy / d * 6;
                if (!hit(px, py)) { cx = px; cy = py; }
                spd *= 0.7f;
            }
        }
        int S = SP[map];
        float tx = (S * TG[map][mission][0] + 1) * T, ty = (S * TG[map][mission][1] + 1) * T;
        if (Math.hypot(cx - tx, cy - ty) < 60) {
            mission++;
            if (mission >= 3) {
                state = 2;
                int nu = Math.min(3, map + 2);
                if (unlocked < nu) { unlocked = nu; sp.edit().putInt("u", nu).apply(); }
            }
        }
    }

    void txt(Canvas c, String s, float x, float y, float size, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size); p.setColor(col);
        c.drawText(s, x, y, p);
    }

    void draw(Canvas c) {
        c.drawColor(0xFF1B1B22);
        if (state == 0) { menu(c); return; }
        if (state == 2) {
            txt(c, "Minden küldetés kész!", W / 2f, H * 0.35f, H * 0.1f, 0xFFFFFFFF);
            txt(c, map < 2 ? (map + 2) + ". térkép feloldva!" : "Mind a 3 térkép kész!", W / 2f, H * 0.55f, H * 0.06f, 0xFFFFD700);
            txt(c, "Érintsd meg a folytatáshoz", W / 2f, H * 0.75f, H * 0.05f, 0xFFAAAAAA);
            return;
        }
        Bitmap b = bmp; Canvas bc = bcv;
        if (b == null || bc == null) return;
        int S = SP[map];
        float z = LH / 600f, lw = b.getWidth();
        float camX = cx - lw / (2 * z), camY = cy - LH / (2 * z);
        bc.drawColor(0xFF1B1B22);
        bc.save();
        bc.scale(z, z);
        bc.translate(-camX, -camY);
        int x0 = Math.max(0, (int) (camX / T)), x1 = Math.min(N - 1, (int) ((camX + lw / z) / T));
        int y0 = Math.max(0, (int) (camY / T)), y1 = Math.min(N - 1, (int) ((camY + LH / z) / T));
        wp.setStyle(Paint.Style.FILL);
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++) {
                if (solid[x][y]) {
                    int k = (x * 7 + y * 13) % 3;
                    wp.setColor(BASE[map][k]);
                    bc.drawRect(x * T, y * T, x * T + T, y * T + T, wp);
                    wp.setColor(ROOF[map][k]);
                    bc.drawRect(x * T + 6, y * T + 6, x * T + T - 6, y * T + T - 6, wp);
                } else {
                    wp.setColor(ROAD[map]);
                    bc.drawRect(x * T, y * T, x * T + T, y * T + T, wp);
                    wp.setColor(0xFFDDDDDD);
                    if (y % S == 0 && x % S >= 2) bc.drawRect(x * T + 8, y * T + T - 3, x * T + T - 8, y * T + T + 3, wp);
                    if (x % S == 0 && y % S >= 2) bc.drawRect(x * T + T - 3, y * T + 8, x * T + T + 3, y * T + T - 8, wp);
                }
            }
        float tx = (S * TG[map][mission][0] + 1) * T, ty = (S * TG[map][mission][1] + 1) * T;
        float pr = 34 + 6 * (float) Math.sin(System.currentTimeMillis() / 200.0);
        wp.setStyle(Paint.Style.STROKE); wp.setStrokeWidth(8); wp.setColor(0xFFFFD700);
        bc.drawCircle(tx, ty, pr, wp);
        wp.setStyle(Paint.Style.FILL);
        for (int i = 0; i < AC; i++) {
            bc.save();
            bc.translate(ax[i], ay[i]);
            bc.rotate(adir[i] * 90f);
            wp.setColor(CAR[i % 4]); bc.drawRect(-20, -11, 20, 11, wp);
            wp.setColor(0xFF9FD3F5); bc.drawRect(2, -8, 11, 8, wp);
            bc.restore();
        }
        bc.save();
        bc.translate(cx, cy);
        bc.rotate(ang * 57.29578f);
        wp.setColor(0xFFD32F2F); bc.drawRect(-20, -11, 20, 11, wp);
        wp.setColor(0xFF9FD3F5); bc.drawRect(2, -8, 11, 8, wp);
        bc.restore();
        bc.restore();
        c.drawBitmap(b, null, dst, fp);
        // HUD
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(H * 0.05f);
        p.setColor(0x88000000);
        c.drawRect(0, 0, W * 0.7f, H * 0.09f, p);
        p.setColor(0xFFFFFFFF);
        c.drawText(MT[map][mission], H * 0.02f, H * 0.065f, p);
        float ar = H * 0.1f, ay0 = H * 0.16f, s = H * 0.05f;
        float a = (float) Math.atan2(ty - cy, tx - cx);
        c.save(); c.translate(W - ar, ay0); c.rotate(a * 57.29578f);
        Path path = new Path();
        path.moveTo(s, 0); path.lineTo(-s, -s * 0.7f); path.lineTo(-s, s * 0.7f); path.close();
        p.setColor(0xFFFFD700); c.drawPath(path, p);
        c.restore();
        btn(c, bl, "<", left); btn(c, br, ">", right);
        btn(c, bg, "GÁZ", gas); btn(c, bk, "FÉK", brake);
    }

    void btn(Canvas c, RectF r, String s, boolean on) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? 0xAAFFFFFF : 0x55FFFFFF);
        c.drawRect(r, p);
        txt(c, s, r.centerX(), r.centerY() + H * 0.025f, H * 0.07f, 0xFF000000);
    }

    void menu(Canvas c) {
        txt(c, "8-BIT FELONY", W / 2f, H * 0.2f, H * 0.12f, 0xFFFFD700);
        for (int i = 0; i < 3; i++) {
            boolean ok = i < unlocked;
            p.setStyle(Paint.Style.FILL);
            p.setColor(ok ? 0xFF2E7D32 : 0xFF555555);
            c.drawRect(mb[i], p);
            txt(c, (i + 1) + ". TÉRKÉP – " + (ok ? MN[i] : "ZÁROLVA"), mb[i].centerX(), mb[i].centerY() + H * 0.02f, H * 0.06f, 0xFFFFFFFF);
        }
        txt(c, "Teljesítsd a küldetéseket a következő térkép feloldásához", W / 2f, H * 0.96f, H * 0.04f, 0xFFAAAAAA);
    }
}
