package de.bentigorlich.mimic3ttsenginewrapper.util;

import com.google.gson.Gson;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import de.bentigorlich.mimic3ttsenginewrapper.entities.LogEntity;

public class JsonFormatter extends Formatter {

    Gson _Gson;

    public JsonFormatter() {
        super();
        _Gson = new Gson();
    }

    @Override
    public String format(LogRecord logRecord) {

        String message = _Gson.toJson(new LogEntity(logRecord)) + "\n";
        return message;
    }
}
