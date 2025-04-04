/**
 * @filename Tone.java
 * @author abbymaureen & nawilliams
 * @date 04/03/25
 *
 * The following class allows for songs to be played and uses multiple threads to play such notes.
 * Each unique note has its own thread which sleeps when unused.
 *
 * Based on a 'hint' from Nate, no additional classes were made in order to make the code most readable.
 * The solution has been attempted using just the Tone class provided in class.
 *
 * Marked spots where ChatGPT modified code for proper playback/threading
 */

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

/**
 *  Class Tone
 *
 *  The following class allows for songs to be played and uses multiple threads to play such notes.
 *  Each unique note has its own thread which sleeps when unused.
 *
 *  Functions
 *  ---------
 *      main : static void
 *          Takes in command line arguments (using ant) to play songs
 *
 *      playSongFromFile : void
 *          Takes in a file as input and plays the notes on the file
 *
 *      playSong : void
 *          Uses threading to sleep and awaken notes for playing
 *
 *      mapDurationToNoteLength : NoteLength
 *          Maps the numeric note input in a file to the proper note length
 */
public class Tone {

    /**
     * main : void
     *
     * @param args - uses ant to sub in alternate files
     *
     * The main function plays the songs inputted into the system
     */
    public static void main(String[] args) {
        final AudioFormat af = new AudioFormat(Note.SAMPLE_RATE, 8, 1, true, false);
        Tone t = new Tone(af);

        String filename = args.length > 0 ? args[0] : "songs/prelude.txt";

        // Run playback in a separate thread
        new Thread(() -> {
            try {
                t.playSongFromFile(filename);
            } catch (Exception e) {
                System.err.println("An error occurred while playing the song: " + e.getMessage());
            }
        }).start();
    }

    private final AudioFormat af;

    // Constructor
    Tone(AudioFormat af) {
        this.af = af;
    }

    /**
     * Play Song from File
     *     Plays the song from a given file, handles invalid song formats
     *
     * @param filename : String : the filepath and filename of the song to be played
     */
    public void playSongFromFile(String filename) {
        List<BellNote> song = new ArrayList<>();

        // Try with resources closes automatically, making memory safe - yay!
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length != 2) continue;

                String noteStr = parts[0].trim();
                int durationValue;

                try {
                    durationValue = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException f) {
                    System.err.println("Invalid number format: " + parts[1]);
                    continue;
                }

                Note note;
                try {
                    note = Note.valueOf(noteStr);
                } catch (IllegalArgumentException e) {
                    System.out.println("Skipping invalid note: " + noteStr);
                    continue;
                }

                NoteLength length = mapDurationToNoteLength(durationValue);
                if (length == null) {
                    System.out.println("Skipping invalid duration: " + durationValue);
                    continue;
                }

                song.add(new BellNote(note, length));
            }

            // Only attempt to play if we successfully parsed something
            // From ChatGPT - was encountering LineNotAvailable errors, this fixes that
            if (!song.isEmpty()) {
                playSong(song);
            } else {
                System.out.println("No valid notes to play.");
            }

        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filename);
        } catch (IOException e) {
            System.err.println("IOException while reading file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during song loading: " + e.getMessage());
        }
    }

    /**
     * Play Song
     *     Plays the song based off of a list of BellNotes
     *
     * @param song : ArrayList : A list of BellNotes that make up the song
     */
    private void playSong(List<BellNote> song) {
        // Create our 'conductor' which controls the timing of our song
        ScheduledExecutorService conductor = Executors.newSingleThreadScheduledExecutor();
        // Establish a delay so that the notes don't ever play on top of each other
        long delay = 0;

        // Loop through all the notes in the song and schedule their play time
        for (BellNote bn : song) {
            final BellNote note = bn;
            conductor.schedule(() -> {
                try {
                    playNote(note);
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }, delay, TimeUnit.MILLISECONDS);

            delay += bn.length.timeMs();
        }

        // Catch the potential interrupted wait time
        try {
            conductor.awaitTermination(delay, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        conductor.shutdown();
    }

    /**
     * Map Duration to Note Length
     *     Maps the numeric note length to the proper BellNote length
     *
     * @param duration : int : The numeric duration of the note
     * @return switch case for the duration
     */
    private NoteLength mapDurationToNoteLength(int duration) {
        return switch (duration) {
            case 1 -> NoteLength.WHOLE;
            case 2 -> NoteLength.HALF;
            case 4 -> NoteLength.QUARTER;
            case 8 -> NoteLength.EIGTH;
            default -> null; // Unsupported duration
        };
    }

    /**
     * Play Note
     *     Play the BellNote
     *
     * @param bn : BellNote : The BellNote to be played
     * @throws LineUnavailableException - caught in playSongFromFile
     */
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

/**
 * Class BellNote
 *
 * The following class sets up how to play a BellNote
 * Adapted from Nate Williams code
 *
 * Enumerations
 * ---------
 *     NoteLength : uses floats to convert into note lengths
 *     Note : uses Strings to convert into note names
 */
class BellNote {
    final Note note;
    final NoteLength length;

    // Constructor
    BellNote(Note note, NoteLength length) {
        this.note = note;
        this.length = length;
    }
}

/***
 * Enumeration NoteLength
 *
 * Takes in numbers and converts into properly timed note lengths
 *
 * Functions
 * ---------
 *     timeMs : int : returns the note length in milliseconds
 */
enum NoteLength {
    WHOLE(1.0f), HALF(0.5f), QUARTER(0.25f), EIGTH(0.125f);

    private final int timeMs;

    // Constructor
    // Takes in the float of the length of a note and converts it to milliseconds
    private NoteLength(float length) {
        timeMs = (int) (length * Note.MEASURE_LENGTH_SEC * 1000);
    }

    /**
     * Time in Milliseconds
     *     Returns the length of the note in milliseconds
     *
     * @return int : time in milliseconds
     */
    public int timeMs() {
        return timeMs;
    }
}

/**
 * Enumeration Note
 *
 * Takes in a string based on the name of the note
 *
 * Functions
 * ---------
 *     sample : byte[] : the sound bite of the note
 */
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

    // Constructor
    // Sets up the calculations for creating a note with the proper frequency and length
    private Note() {
        int n = this.ordinal();
        if (n > 0) {
            // Calculate the frequency!
            final double halfStepUpFromA = n - 1;
            final double exp = halfStepUpFromA / 12.0d;
            final double freq = FREQUENCY_A_HZ * Math.pow(2.0d, exp);

            // Use 'sine' to change the data sample for the desired frequency
            final double sinStep = freq * step_alpha;
            for (int i = 0; i < sinSample.length; i++) {
                sinSample[i] = (byte) (Math.sin(i * sinStep) * MAX_VOLUME);
            }
        }
    }

    /**
     * Sample
     *     The sample sound of the song
     *
     * @return byte[] : list of the sound bites of the program
     */
    public byte[] sample() {
        return sinSample;
    }
}