import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Tone {

    public static void main(String[] args) throws Exception {
        final AudioFormat af = new AudioFormat(Note.SAMPLE_RATE, 8, 1, true, false);
        Tone t = new Tone(af);

        // Path to the song file
        String filename = "songs/prelude.txt";

        System.out.println("Playing song from file: " + filename);
        t.playSongFromFile(filename);
        System.out.println("Finished playing.");
    }

    private final AudioFormat af;

    Tone(AudioFormat af) {
        this.af = af;
    }

    void playSong(List<BellNote> song) throws LineUnavailableException {
        try (final SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
            line.open();
            line.start();

            for (BellNote bn: song) {
                playNote(line, bn);
            }
            line.drain();
        }
    }

    public void playSongFromFile(String filename) throws Exception {
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
        }

        // Play the parsed song using existing playSong method
        playSong(song);
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


    private void playNote(SourceDataLine line, BellNote bn) {
        final int ms = Math.min(bn.length.timeMs(), Note.MEASURE_LENGTH_SEC * 1000);
        final int length = Note.SAMPLE_RATE * ms / 1000;
        line.write(bn.note.sample(), 0, length);
        line.write(Note.REST.sample(), 0, 50);
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
    WHOLE(1.0f),
    HALF(0.5f),
    QUARTER(0.25f),
    EIGTH(0.125f);

    private final int timeMs;

    private NoteLength(float length) {
        timeMs = (int)(length * Note.MEASURE_LENGTH_SEC * 1000);
    }

    public int timeMs() {
        return timeMs;
    }
}

enum Note {
    // REST Must be the first 'Note'
    REST,

    // Octave 2
    C2, C2S, D2, D2S, E2, F2, F2S, G2, G2S, A2, A2S, B2,

    // Octave 3
    C3, C3S, D3, D3S, E3, F3, F3S, G3, G3S, A3, A3S, B3,

    // Octave 4
    C4, C4S, D4, D4S, E4, F4, F4S, G4, G4S, A4, A4S, B4,

    // Octave 5
    C5, C5S, D5, D5S, E5, F5, F5S, G5, G5S, A5, A5S, B5,

    // Octave 6
    C6;

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
            final double halfStepUpFromA = n - 1 - 36; // Adjust for C2 being index 1
            final double exp = halfStepUpFromA / 12.0d;
            final double freq = FREQUENCY_A_HZ * Math.pow(2.0d, exp);

            // Create sinusoidal data sample for the desired frequency
            final double sinStep = freq * step_alpha;
            for (int i = 0; i < sinSample.length; i++) {
                sinSample[i] = (byte)(Math.sin(i * sinStep) * MAX_VOLUME);
            }
        }
    }

    public byte[] sample() {
        return sinSample;
    }
}
