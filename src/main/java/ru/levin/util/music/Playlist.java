package ru.levin.util.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.List;

// Очередь треков под ОДНИМ локом: все compound-операции (next/prev/set/add/clear) синхронны, чтобы
// пользовательский next и авто-переход по TrackEndEvent (разные потоки) не гонялись (double-skip/IOOBE).
public final class Playlist {
    private final Object lock = new Object();
    private final List<AudioTrack> tracks = new ArrayList<>();
    private int index = -1;

    public void set(AudioTrack t) {
        synchronized (lock) {
            tracks.clear();
            tracks.add(t);
            index = 0;
        }
    }

    public void setAll(List<AudioTrack> ts) {
        synchronized (lock) {
            tracks.clear();
            tracks.addAll(ts);
            index = tracks.isEmpty() ? -1 : 0;
        }
    }

    public AudioTrack current() {
        synchronized (lock) {
            return (index >= 0 && index < tracks.size()) ? tracks.get(index) : null;
        }
    }

    // следующий трек; при loop возвращается к началу, иначе null в конце
    public AudioTrack next(boolean loop) {
        synchronized (lock) {
            if (tracks.isEmpty()) return null;
            index++;
            if (index >= tracks.size()) {
                if (loop) index = 0;
                else { index = tracks.size(); return null; }
            }
            return tracks.get(index);
        }
    }

    public AudioTrack prev() {
        synchronized (lock) {
            if (tracks.isEmpty()) return null;
            index = Math.max(0, index - 1);
            return tracks.get(index);
        }
    }

    // append в конец очереди; если ничего не играло — стартуем с него
    public AudioTrack add(AudioTrack t) {
        synchronized (lock) {
            tracks.add(t);
            trim();
            if (index < 0) { index = 0; return tracks.get(0); }
            return null;
        }
    }

    // append всех (плейлист в очередь)
    public void addAll(List<AudioTrack> ts) {
        synchronized (lock) {
            tracks.addAll(ts);
            trim();
            if (index < 0 && !tracks.isEmpty()) index = 0;
        }
    }

    // вставить сразу ПОСЛЕ текущего (play-next)
    public AudioTrack insertNext(AudioTrack t) {
        synchronized (lock) {
            if (tracks.isEmpty()) { tracks.add(t); index = 0; return tracks.get(0); }
            tracks.add(Math.min(index + 1, tracks.size()), t);
            trim();
            return null;
        }
    }

    // play-now с сохранением истории: append + прыжок на него (prev вернётся к прошлым)
    public AudioTrack addJump(AudioTrack t) {
        synchronized (lock) {
            tracks.add(t);
            trim();
            index = tracks.size() - 1;
            return tracks.get(index);
        }
    }

    // ограничиваем рост истории: режем самые старые (спереди), сдвигая index
    private void trim() {
        final int CAP = 200;
        while (tracks.size() > CAP) {
            tracks.remove(0);
            if (index > 0) index--;
        }
    }

    public java.util.List<AudioTrack> snapshot() {
        synchronized (lock) { return new ArrayList<>(tracks); }
    }

    public void setIndex(int i) {
        synchronized (lock) {
            if (tracks.isEmpty()) { index = -1; return; }
            index = Math.max(0, Math.min(i, tracks.size() - 1));
        }
    }

    // удалить трек по индексу; корректируем позицию (текущий трек продолжит играть — это лишь правка очереди)
    public void remove(int i) {
        synchronized (lock) {
            if (i < 0 || i >= tracks.size()) return;
            tracks.remove(i);
            if (i < index) index--;
            if (index >= tracks.size()) index = tracks.size() - 1;
        }
    }

    public void clear() {
        synchronized (lock) {
            tracks.clear();
            index = -1;
        }
    }

    public int size() {
        synchronized (lock) { return tracks.size(); }
    }

    public int position() {
        synchronized (lock) { return index; }
    }
}
