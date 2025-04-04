import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.*;

public class Tone {

    public static void main(String[] args) throws Exception {
        final AudioFormat af = new AudioFormat(Note.SAMPLE_RATE, 8, 1, true, false);
        Tone t = new Tone(af);

        String filename = args.length > 0 ? args[0] : "songs/prelude.txt";

        System.out.println("Playing song from file: " + filename);

        // Run playback in a separate thread
        new Thread(() -> {
            try {
                t.playSongFromFile(filename);
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }).start();

        System.out.println("Playback started in background thread...");
    }

    private final AudioFormat af;

    Tone(AudioFormat af) {
        this.af = af;
    }

    public void playSongFromFile(String filename) throws LineUnavailableException {
        List<BellNote> song = new ArrayList<>();

        // Catch the file - don't throw the exception
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length != 2) continue;

                String noteStr = parts[0].trim();
                int durationValue = Integer.parseInt(parts[1].trim());

                Note note;
                try {
                    note = Note.valueOf(noteStr); // Uses existing Note enum
                } catch (IllegalArgumentException e) {
                    System.out.println("Skipping invalid note: " + noteStr);
                    continue;
                }

                NoteLength length = mapDurationToNoteLength(durationValue); // Uses existing NoteLength enum
                if (length == null) {
                    System.out.println("Skipping invalid duration: " + durationValue);
                    continue;
                }

                song.add(new BellNote(note, length)); // Uses existing BellNote class
            }
        } catch (IOException e) {
            // handle IOException
        } catch (NumberFormatException f) {
            // handle NumberFormatException
        } catch (IllegalArgumentException i) {
            // handle IllegalArgumentException
        } catch (NullPointerException j) {
            // handle NullPointerException
        }

        // Play the parsed song using existing playSong method
        playSong(song);
    }

    private void playSong(List<BellNote> song) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        long delay = 0;

        for (BellNote bn : song) {
            final BellNote note = bn;
            executor.schedule(() -> {
                try {
                    playNote(note);
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }, delay, TimeUnit.MILLISECONDS);

            delay += bn.length.timeMs();
        }

        try {
            executor.awaitTermination(delay, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();
    }

    private NoteLength mapDurationToNoteLength(int duration) {
        return switch (duration) {
            case 1 -> NoteLength.WHOLE;
            case 2 -> NoteLength.HALF;
            case 4 -> NoteLength.QUARTER;
            case 8 -> NoteLength.EIGTH;
            default -> null; // Unsupported duration
        };
    }

    private void playNote(BellNote bn) throws LineUnavailableException {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
            line.open();
            line.start();
            final int ms = Math.min(bn.length.timeMs(), Note.MEASURE_LENGTH_SEC * 1000);
            final int length = Note.SAMPLE_RATE * ms / 1000;
            line.write(bn.note.sample(), 0, length);
            line.write(Note.REST.sample(), 0, 50);
            line.drain();
        }
    }
}

class BellNote {
    final Note note;
    final NoteLength length;

    BellNote(Note note, NoteLength length) {
        this.note = note;
        this.length = length;
    }
}

enum NoteLength {
    WHOLE(1.0f), HALF(0.5f), QUARTER(0.25f), EIGTH(0.125f);

    private final int timeMs;

    private NoteLength(float length) {
        timeMs = (int) (length * Note.MEASURE_LENGTH_SEC * 1000);
    }

    public int timeMs() {
        return timeMs;
    }
}

enum Note {
    // REST Must be the first 'Note'
    REST, A4, A4S, B4, C4, C4S, D4, D4S, E4, F4, F4S, G4, G4S, A5, B5, C5, C5S, D5, E5, E5S, F5, G5, C6;

    public static final int SAMPLE_RATE = 48 * 1024; // ~48KHz
    public static final int MEASURE_LENGTH_SEC = 1;

    // Circumference of a circle divided by # of samples
    private static final double step_alpha = (2.0d * Math.PI) / SAMPLE_RATE;

    private final double FREQUENCY_A_HZ = 440.0d;
    private final double MAX_VOLUME = 127.0d;

    private final byte[] sinSample = new byte[MEASURE_LENGTH_SEC * SAMPLE_RATE];

    private Note() {
        int n = this.ordinal();
        if (n > 0) {
            // Calculate the frequency!
            final double halfStepUpFromA = n - 1;
            final double exp = halfStepUpFromA / 12.0d;
            final double freq = FREQUENCY_A_HZ * Math.pow(2.0d, exp);

            // Create sinusoidal data sample for the desired frequency
            final double sinStep = freq * step_alpha;
            for (int i = 0; i < sinSample.length; i++) {
                sinSample[i] = (byte) (Math.sin(i * sinStep) * MAX_VOLUME);
            }
        }
    }

    public byte[] sample() {
        return sinSample;
    }
}