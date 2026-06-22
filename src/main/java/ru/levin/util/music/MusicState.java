package ru.levin.util.music;

// Состояние плеера для HUD-баннера и логики.
public enum MusicState {
    IDLE,      // ничего не загружено
    LOADING,   // идёт резолв/буферизация
    PLAYING,   // играет
    PAUSED,    // на паузе пользователем
    ENDED,     // очередь закончилась
    ERROR      // ошибка (см. errorMessage)
}
