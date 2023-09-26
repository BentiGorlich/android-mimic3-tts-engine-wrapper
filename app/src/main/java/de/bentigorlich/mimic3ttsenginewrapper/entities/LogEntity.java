package de.bentigorlich.mimic3ttsenginewrapper.entities;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.LogRecord;

public class LogEntity implements Cloneable {
    public String Message;
    public String Level;
    public String SourceClassName;
    public String SourceMethodName;
    public long Timestamp;
    public String LoggerName;

    public LogEntity(LogRecord record) {
        Message = record.getMessage();
        Level = record.getLevel().toString();
        SourceClassName = record.getSourceClassName();
        SourceMethodName = record.getSourceMethodName();
        Timestamp = record.getMillis();
        LoggerName = record.getLoggerName();
    }

    public LogEntity() {}

    @NonNull
    @Override
    public LogEntity clone() {
        LogEntity copy = new LogEntity();
        copy.LoggerName = LoggerName;
        copy.Level = Level;
        copy.Timestamp = Timestamp;
        copy.Message = Message;
        copy.SourceClassName = SourceClassName;
        copy.SourceMethodName = SourceMethodName;
        return copy;
    }

    public String GetText(boolean includeSource) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Timestamp);
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy|HH:mm:ss");
        String source = "";
        if(includeSource) {
            if (LoggerName != null && !LoggerName.equals(""))
                source += "[" + LoggerName + "]";

            if (SourceClassName != null && !SourceClassName.equals(""))
                source += "[" + SourceClassName + "]";

            if (SourceMethodName != null && !SourceMethodName.equals(""))
                source += "[" + SourceMethodName + "]";
        }
        return "<" + formatter.format(c.getTime()) + " " + Level + ">" + source + " " + Message;
    }
}
