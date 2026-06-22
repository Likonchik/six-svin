package ru.levin.util.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

// Один daemon-поток вывода: тянет PCM-фреймы из lavaplayer (AudioPlayer.provide()) и пишет в
// javax.sound SourceDataLine. Громкость — ручное per-sample масштабирование (int16 LE). Пауза/гейтинг
// (legitMode / пауза игры / потеря фокуса) опрашивается КАЖДУЮ итерацию через pausedSupplier, поэтому
// работает независимо от Event.call (который коротит вне мира и в legitMode).
public final class AudioOutput {

    // lavaplayer DISCORD_PCM_S16_LE: 48кГц, 16 бит, стерео, little-endian
    private static final AudioFormat FORMAT = new AudioFormat(48000f, 16, 2, true, false);
    private static final int FRAME_BYTES = 3840;          // 20мс стерео-фрейма
    private static final int LINE_BUFFER = FRAME_BYTES * 8;

    private final AudioPlayer player;
    private final BooleanSupplier paused;   // true => не тянуть и не писать (пауза/гейт)
    private final DoubleSupplier gain;      // 0..1 итоговый линейный множитель

    private volatile boolean running;
    private volatile String error;
    private Thread thread;
    private SourceDataLine line;

    public AudioOutput(AudioPlayer player, BooleanSupplier paused, DoubleSupplier gain) {
        this.player = player;
        this.paused = paused;
        this.gain = gain;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "OneTap-MusicOutput");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (line != null) { line.stop(); line.flush(); }  // разблокировать возможный блокирующий write
        } catch (Throwable ignored) {}
        if (thread != null) thread.interrupt();
    }

    public String getError() { return error; }

    private void run() {
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, LINE_BUFFER);
            line.start();
            System.out.println("[MediaPlayer] audio line opened: " + line.getFormat() + " bufferSize=" + line.getBufferSize());
        } catch (Throwable t) {
            error = "Аудиоустройство недоступно";
            running = false;
            System.err.println("[MediaPlayer] audio line open FAILED: " + t);
            return;
        }

        boolean wasPaused = false;
        long frames = 0, nulls = 0, lastLog = System.currentTimeMillis();
        while (running) {
            try {
                boolean p = paused.getAsBoolean();
                if (p != wasPaused) { try { player.setPaused(p); } catch (Throwable ignored) {} } // только на смене

                long now = System.currentTimeMillis();
                if (now - lastLog > 2000) {
                    System.out.println("[MediaPlayer] out: paused=" + p + " frames=" + frames + " nulls=" + nulls
                            + " gain=" + String.format("%.3f", gain.getAsDouble()) + " lineActive=" + line.isActive());
                    lastLog = now;
                }

                if (p) {
                    if (!wasPaused) { try { line.flush(); } catch (Throwable ignored) {} wasPaused = true; }
                    Thread.sleep(20);
                    continue;
                }
                wasPaused = false;

                AudioFrame frame = player.provide();
                if (frame == null) {            // фрейм ещё не готов (буферизация/сеть) — не EOF
                    nulls++;
                    Thread.sleep(5);
                    continue;
                }
                byte[] data = frame.getData();
                applyGain(data, gain.getAsDouble());
                line.write(data, 0, data.length);   // блокируется => естественный темп вывода
                if (frames == 0) System.out.println("[MediaPlayer] first audio frame written, len=" + data.length);
                frames++;
            } catch (InterruptedException ie) {
                break;
            } catch (Throwable t) {
                // не валим поток на единичной ошибке
                try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
            }
        }

        try { line.stop(); line.flush(); line.close(); } catch (Throwable ignored) {}
    }

    // per-sample int16 LE масштабирование. gain<=0 => тишина, gain>=1 => без изменений.
    private static void applyGain(byte[] data, double g) {
        if (g >= 0.999) return;
        if (g <= 0.0) { java.util.Arrays.fill(data, (byte) 0); return; }
        for (int i = 0; i + 1 < data.length; i += 2) {
            short s = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
            int v = (int) (s * g);
            if (v > 32767) v = 32767;
            else if (v < -32768) v = -32768;
            data[i] = (byte) (v & 0xFF);
            data[i + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }
}
