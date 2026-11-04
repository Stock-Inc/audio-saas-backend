package backend.app.api.backend.audio.controller.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Статус загрузки: RECEIVED — принята, FORWARDED — передана обработчику, FAILED — ошибка передачи"
)
public enum AudioStatus {
    RECEIVED,
    FORWARDED,
    FAILED
}
