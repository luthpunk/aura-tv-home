package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Apple Weather Regional Precipitation Radar Map Card View (Edisi Indonesia).
 * Dynamically renders the active Indonesian city, its province, surrounding sea/coastline,
 * real neighboring Indonesian cities by coordinate offset, and Doppler radar cells.
 */
public class RadarMapView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coastStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int temp = 29;
    private String locationName = "Tuban";
    private String provinceName = "Jawa Timur";
    private double latitude = AppLocationManager.DEFAULT_LATITUDE;
    private double longitude = AppLocationManager.DEFAULT_LONGITUDE;

    public RadarMapView(Context context) {
        this(context, null);
    }

    public RadarMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF111C2B);

        landPaint.setStyle(Paint.Style.FILL);
        landPaint.setColor(0xFF1C2C3E);

        coastStrokePaint.setStyle(Paint.Style.STROKE);
        coastStrokePaint.setStrokeWidth(1.2f);
        coastStrokePaint.setColor(0x3364D2FF);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xCCFFFFFF);

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFF1E3A8A);

        badgeStroke.setStyle(Paint.Style.STROKE);
        badgeStroke.setStrokeWidth(1.5f);
        badgeStroke.setColor(0xFFFFFFFF);
    }

    public void setDetails(int temp, String loc) {
        Context ctx = getContext();
        double lat = ctx != null ? AppLocationManager.getLatitude(ctx) : AppLocationManager.DEFAULT_LATITUDE;
        double lon = ctx != null ? AppLocationManager.getLongitude(ctx) : AppLocationManager.DEFAULT_LONGITUDE;
        String prov = ctx != null ? AppLocationManager.getActiveProvince(ctx) : "Jawa Timur";
        setDetails(temp, loc, lat, lon, prov);
    }

    public void setDetails(int temp, String loc, double lat, double lon, String province) {
        this.temp = temp;
        if (loc != null && !loc.trim().isEmpty()) {
            this.locationName = loc.trim();
        }
        this.latitude = lat;
        this.longitude = lon;
        if (province != null && !province.trim().isEmpty()) {
            this.provinceName = province.trim();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // Clip to rounded corners
        Path clipPath = new Path();
        clipPath.addRoundRect(new RectF(0, 0, w, h), 14f * density, 14f * density, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // 1. Sea / Ocean background
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 2. Regional Indonesian Landmass Polygon & Sea Label based on coordinates
        String seaLabel = drawIndonesianGeography(canvas, w, h, latitude, longitude);

        // 3. Radar Precipitation Heatmap Overlays (Apple Weather Doppler return)
        RadialGradient cell1 = new RadialGradient(
                w * 0.42f, h * 0.48f, w * 0.34f,
                new int[]{0xCCDB2777, 0x997C3AED, 0x552563EB, 0x001E3A8A},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell1);
        canvas.drawCircle(w * 0.42f, h * 0.48f, w * 0.34f, radarPaint);

        RadialGradient cell2 = new RadialGradient(
                w * 0.60f, h * 0.64f, w * 0.25f,
                new int[]{0xBBE11D48, 0x88F59E0B, 0x4410B981, 0x000D9488},
                new float[]{0f, 0.40f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell2);
        canvas.drawCircle(w * 0.60f, h * 0.64f, w * 0.25f, radarPaint);

        // 4. Center Active Location Badge coordinates
        float badgeX = w * 0.48f;
        float badgeY = h * 0.52f;
        float bRadius = 9f * density;

        List<RectF> occupied = new ArrayList<>();
        // Reserve space around the center badge + city label below it
        occupied.add(new RectF(
                badgeX - 38f * density,
                badgeY - 14f * density,
                badgeX + 38f * density,
                badgeY + 20f * density
        ));

        // 5. Province Label (e.g. JAWA TIMUR, DKI JAKARTA, BALI)
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(6.8f * density);
        textPaint.setColor(0x88CBD5E1);
        textPaint.setFakeBoldText(true);
        String provUpper = provinceName.toUpperCase(Locale.ROOT);
        float provW = textPaint.measureText(provUpper);
        float provX = Math.max(8f * density, (badgeX - provW * 0.5f));
        float provY = h * 0.78f;
        canvas.drawText(provUpper, provX, provY, textPaint);
        occupied.add(new RectF(provX - 4f * density, provY - 9f * density, provX + provW + 4f * density, provY + 3f * density));

        // 6. Sea Label
        if (seaLabel != null && !seaLabel.isEmpty()) {
            textPaint.setTextSize(6.5f * density);
            textPaint.setColor(0x7593C5FD);
            textPaint.setFakeBoldText(false);
            float seaW = textPaint.measureText(seaLabel);
            boolean seaAtTop = isNorthCoast(latitude, longitude);
            float seaX = seaAtTop ? (w * 0.10f) : (w - seaW - 10f * density);
            float seaY = seaAtTop ? (h * 0.16f) : (h * 0.92f);
            canvas.drawText(seaLabel, seaX, seaY, textPaint);
            occupied.add(new RectF(seaX - 4f * density, seaY - 8f * density, seaX + seaW + 4f * density, seaY + 3f * density));
        }

        // 7. Plot Real Nearby Indonesian Cities around (latitude, longitude)
        List<AppLocationManager.Station> nearby = AppLocationManager.getNearbyCities(latitude, longitude, 10);
        if (!nearby.isEmpty()) {
            // Compute adaptive geographic scale so neighboring cities fill the radar view naturally
            double maxDelta = 0.45;
            int sampleCount = Math.min(5, nearby.size());
            for (int i = 0; i < sampleCount; i++) {
                AppLocationManager.Station s = nearby.get(i);
                double dLon = Math.abs(s.lon - longitude);
                double dLat = Math.abs(s.lat - latitude);
                if (dLon > maxDelta) maxDelta = dLon;
                if (dLat > maxDelta) maxDelta = dLat;
            }
            float scaleX = (w * 0.36f) / (float) maxDelta;
            float scaleY = (h * 0.34f) / (float) maxDelta;

            textPaint.setTextSize(7.2f * density);
            int drawnCount = 0;
            for (AppLocationManager.Station s : nearby) {
                if (drawnCount >= 6) break;
                String label = s.getShortMapLabel();
                if (label.equalsIgnoreCase(locationName)) continue;

                float dx = (float) (s.lon - longitude) * scaleX;
                float dy = (float) -(s.lat - latitude) * scaleY; // North is up (-Y)

                float cx = badgeX + dx;
                float cy = badgeY + dy;

                // Clamp within visible radar card margins
                float labelW = textPaint.measureText(label);
                cx = Math.max(10f * density, Math.min(w - labelW - 12f * density, cx));
                cy = Math.max(14f * density, Math.min(h - 8f * density, cy));

                RectF box = new RectF(cx - 4f * density, cy - 9f * density, cx + labelW + 8f * density, cy + 4f * density);
                boolean collides = false;
                for (RectF occ : occupied) {
                    if (RectF.intersects(box, occ)) {
                        collides = true;
                        break;
                    }
                }
                if (collides) continue;

                occupied.add(box);
                // Small city dot + label
                textPaint.setColor(0xB8FFFFFF);
                textPaint.setFakeBoldText(false);
                canvas.drawCircle(cx, cy - 2.5f * density, 1.6f * density, textPaint);
                canvas.drawText(label, cx + 4f * density, cy, textPaint);
                drawnCount++;
            }
        }

        // 8. Active City Temperature Badge (Circular disc: temp + active Indonesian city name)
        canvas.drawCircle(badgeX, badgeY, bRadius, badgePaint);
        canvas.drawCircle(badgeX, badgeY, bRadius, badgeStroke);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(7.5f * density);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(temp), badgeX, badgeY + (2.5f * density), textPaint);

        String cleanCity = locationName;
        int paren = cleanCity.indexOf(" (");
        if (paren > 0) cleanCity = cleanCity.substring(0, paren);

        textPaint.setTextSize(7.2f * density);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(cleanCity, badgeX, badgeY + (12f * density), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private boolean isNorthCoast(double lat, double lon) {
        // Pantura Jawa (Banten, Jakarta, Cirebon, Tegal, Semarang, Rembang, Tuban, Lamongan, Gresik, Surabaya, Madura, Situbondo)
        return (lat >= -7.55 && lat <= -5.4 && lon >= 106.0 && lon <= 114.5);
    }

    private String drawIndonesianGeography(Canvas canvas, int w, int h, double lat, double lon) {
        Path land = new Path();
        if (isNorthCoast(lat, lon)) {
            // Pantura Jawa: Laut Jawa in the North (top), Pulau Jawa landmass in the South
            land.moveTo(0, h * 0.34f);
            land.lineTo(w * 0.18f, h * 0.28f);
            land.lineTo(w * 0.38f, h * 0.31f);
            land.lineTo(w * 0.56f, h * 0.26f);
            land.lineTo(w * 0.75f, h * 0.36f);
            land.lineTo(w, h * 0.38f);
            land.lineTo(w, h);
            land.lineTo(0, h);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);

            // If East Java Pantura (Tuban / Lamongan / Gresik / Surabaya / Madura), also draw Madura Island in NE
            if (lon >= 111.5 && lon <= 114.3) {
                Path madura = new Path();
                madura.moveTo(w * 0.66f, h * 0.16f);
                madura.lineTo(w * 0.96f, h * 0.12f);
                madura.lineTo(w * 0.98f, h * 0.24f);
                madura.lineTo(w * 0.70f, h * 0.26f);
                madura.close();
                canvas.drawPath(madura, landPaint);
                canvas.drawPath(madura, coastStrokePaint);
            }
            return "LAUT JAWA";
        } else if (lat < -7.55 && lon >= 106.0 && lon <= 114.5) {
            // Jawa Bagian Selatan & Tengah: Landmass in North/Center, Samudra Hindia in the South
            land.moveTo(0, 0);
            land.lineTo(w, 0);
            land.lineTo(w, h * 0.78f);
            land.lineTo(w * 0.76f, h * 0.74f);
            land.lineTo(w * 0.48f, h * 0.80f);
            land.lineTo(w * 0.22f, h * 0.72f);
            land.lineTo(0, h * 0.76f);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            return "SAMUDRA HINDIA";
        } else if (lon > 114.5 && lat < -7.8) {
            // Bali, NTB, NTT Island chain
            land.moveTo(w * 0.06f, h * 0.42f);
            land.lineTo(w * 0.28f, h * 0.26f);
            land.lineTo(w * 0.68f, h * 0.24f);
            land.lineTo(w * 0.94f, h * 0.44f);
            land.lineTo(w * 0.82f, h * 0.76f);
            land.lineTo(w * 0.44f, h * 0.82f);
            land.lineTo(w * 0.14f, h * 0.70f);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            return lon < 116.0 ? "SELAT BALI" : "LAUT FLORES";
        } else if (lon < 106.5) {
            // Sumatera (Aceh, Sumut, Sumbar, Riau, Kepri, Jambi, Sumsel, Babel, Bengkulu, Lampung)
            land.moveTo(0, 0);
            land.lineTo(w * 0.68f, 0);
            land.lineTo(w * 0.78f, h * 0.35f);
            land.lineTo(w * 0.86f, h * 0.72f);
            land.lineTo(w * 0.74f, h);
            land.lineTo(0, h);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            if (lat > 0.5) return "SELAT MALAKA";
            if (lat < -4.5) return "SELAT SUNDA";
            return "LAUT NATUNA";
        } else if (lon >= 108.5 && lon <= 118.5 && lat >= -4.5 && lat <= 4.5) {
            // Kalimantan
            land.moveTo(0, 0);
            land.lineTo(w * 0.84f, 0);
            land.lineTo(w * 0.78f, h * 0.45f);
            land.lineTo(w * 0.66f, h * 0.82f);
            land.lineTo(0, h * 0.86f);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            return lon > 115.5 ? "SELAT MAKASSAR" : "LAUT JAWA";
        } else if (lon > 118.5 && lon <= 125.5) {
            // Sulawesi
            land.moveTo(w * 0.24f, 0);
            land.lineTo(w * 0.86f, 0);
            land.lineTo(w * 0.72f, h * 0.48f);
            land.lineTo(w * 0.82f, h);
            land.lineTo(w * 0.30f, h);
            land.lineTo(w * 0.20f, h * 0.52f);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            return "SELAT MAKASSAR";
        } else {
            // Maluku & Tanah Papua
            land.moveTo(w * 0.18f, h * 0.18f);
            land.lineTo(w, h * 0.12f);
            land.lineTo(w, h * 0.88f);
            land.lineTo(w * 0.28f, h * 0.82f);
            land.lineTo(w * 0.12f, h * 0.50f);
            land.close();
            canvas.drawPath(land, landPaint);
            canvas.drawPath(land, coastStrokePaint);
            return lon > 132.0 ? "SAMUDRA PASIFIK" : "LAUT BANDA";
        }
    }
}
