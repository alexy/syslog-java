package org.setup.syslog.plain;
import javafx.util.Pair;
import org.joda.time.DateTime;
import org.joda.time.format.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by a on 4/17/14.
 */

// case class SyslogMessage(hostname: Option[String], process: String, pid: Int, message: String)
// case class SyslogEvent(timestamp: DateTime, body: SyslogMessage)

class SyslogMessage {
    String hostname;
    String process;
    int    pid;
    String message;

    SyslogMessage(String hostname, String process, int pid, String message) {
        this.hostname = hostname;
        this.process  = process;
        this.pid      = pid;
        this.message  = message;
    }
}

class SyslogEvent {
    DateTime timestamp;
    SyslogMessage body;

    SyslogEvent(DateTime timestamp, SyslogMessage body) {
        this.timestamp = timestamp;
        this.body = body;
    }
}

public class Syslog {
    static String[] formats = {
            "MMM  d HH:mm:ss", "MMM dd HH:mm:ss",
            "E MMM  d HH:mm:ss", "E MMM dd HH:mm:ss",
            "E MMM  d HH:mm:ss.SSS", "E MMM dd HH:mm:ss.SSS" };

    static List<DateTimeParser> parsers = Arrays.asList(formats).stream()
            .map(format -> DateTimeFormat.forPattern(format).getParser())
            .collect(Collectors.toList());

    static DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .append(null, parsers.toArray(new DateTimeParser[parsers.size()]))
            .toFormatter().withDefaultYear(new DateTime().getYear());

    static String[] dates = { "Apr 17 00:48:38", "Apr 1 00:12:34", "Thu May  1 12:34:00.000" };

    public static void main(String[] args) {
        DateTimeParser[] parsers2 = new DateTimeParser[formats.length];

        for (int i = 0; i < formats.length; i++)
            parsers2[i] = DateTimeFormat.forPattern(formats[i]).getParser();

        DateTimeFormatter formatter2 = new DateTimeFormatterBuilder()
                .append(null, parsers2)
                .toFormatter().withDefaultYear(new DateTime().getYear());

        for (String d : dates) {
            System.out.println(d + "=> " + formatter.parseDateTime(d));
        }

        Pattern reDateTime  = Pattern.compile("(^[A-Za-z0-9, ]+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)(.*)");
        Pattern reSystemLog = Pattern.compile("\\s*(\\S+)\\s+([^\\[]+)\\[(\\d+)\\]\\s*:?\\s*(.*)");

        String systemLogFile = "/var/log/system.log";
        InputStream    fis;
        BufferedReader br;
        String         line;

        List<SyslogEvent> events = new ArrayList<>();

        try {
            fis = new FileInputStream(systemLogFile);
            br  = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
            while ((line = br.readLine()) != null) {
                Matcher matchDateTime = reDateTime.matcher(line);
                if (matchDateTime.find()) {
                    //System.out.println(m.group(1) + " <===> " + m.group(2));
                    String dt   = matchDateTime.group(1);
                    String body = matchDateTime.group(2);

                    Matcher matchMessage = reSystemLog.matcher(body);

                    if (matchMessage.find()) {
                       String host    = matchMessage.group(1);
                       String process = matchMessage.group(2);
                       String pidS    = matchMessage.group(3);
                       String message = matchMessage.group(4);

                       try {
                           DateTime timestamp = formatter.parseDateTime(dt);
                           int pid = Integer.parseInt(pidS);
                           SyslogMessage msg = new SyslogMessage(host, process, pid, message);
                           SyslogEvent   evt = new SyslogEvent(timestamp, msg);

                           events.add(evt);

                       } catch (IllegalArgumentException e) {}
                    }
                }
            }

            Map<String, Integer> procs = new HashMap<>();
            for (SyslogEvent event: events) {
                String key = event.body.process;
                procs.put(key,procs.getOrDefault(key,0)+1);
            }
            List<Map.Entry<String,Integer>> nprocs = new ArrayList<>(procs.entrySet());
            Collections.sort(nprocs, new Comparator<Map.Entry<String,Integer>>() {
                public int compare(Map.Entry<String,Integer> a, Map.Entry<String, Integer> b) {
                        return b.getValue().compareTo(a.getValue());
                    }
                }
            );
            int n = 0;
            for (Map.Entry<String,Integer> nproc: nprocs.subList(0,10)) {
                System.out.println((n++)+": "+nproc.getKey()+" ("+nproc.getValue()+")");
            }

        } catch (FileNotFoundException e) {
            System.out.println("no such file:" + "/var/log/system.log");
        } catch (IOException e) {
            System.out.println("IO error");
        }
    }
}
