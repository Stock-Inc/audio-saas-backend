package backend.app.api.backend.audio.controller.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "RECEIVED — сохранён; FORWARDED — принят ML-сервисом; PROCESSING — обрабатывается; COMPLETED — результат готов; FAILED — ошибка"
)
public enum AudioStatus {
    RECEIVED,
    FORWARDED,
    PROCESSING,
    COMPLETED,
    FAILED
}
