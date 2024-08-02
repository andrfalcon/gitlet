package gitlet;
import java.io.File;
import java.util.HashMap;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** Represents a gitlet commit object.
 *  @author Andrew Falcon
 */
public class Commit implements Serializable {
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = Utils.join(CWD, ".gitlet");
    public static final File COMMIT_DIR = Utils.join(GITLET_DIR, "commits");
    public static final File BLOB_DIR = Utils.join(GITLET_DIR, "blobs");
    public static final File STAGE_DIR = Utils.join(GITLET_DIR, "stages");

    private final String message;
    private final String timestamp;
    private final String parent; // SHA1 ID OF PARENT
    private String secondParent;
    private final String hash;
    private HashMap<String, String> tracking; // filename: blobhash
    private final Date date;

    public Commit(String message, String parent, HashMap<String, String> added,
                  HashMap<String, String> removed, String secondParent) {
        if (parent != null) {
            date = new Date();
            this.tracking = Utils.getCommit(parent).getTracking();
        } else {
            date = new Date(0);
            this.tracking = new HashMap<>();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-08:00"));
        timestamp = sdf.format(date);

        this.message = message;
        this.parent = parent;
        this.secondParent = secondParent;

        for (String filename : added.keySet()) {
            String blobHash = added.get(filename);
            this.tracking.put(filename, blobHash);
        }

        for (String filename : removed.keySet()) {
            this.tracking.remove(filename);
        }

        hash = Utils.sha1(Utils.serialize(this));
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getParent() {
        return parent;
    }

    public String getHash() {
        return hash;
    }

    public boolean containsFile(String filename) {
        return tracking.containsKey(filename);
    }

    // Returns byte array of file in current commit
    public byte[] getFile(String filename) {
        if (tracking.containsKey(filename)) {
            return Utils.readContents(Utils.join(BLOB_DIR, tracking.get(filename)));
        } else {
            return new byte[0];
        }
    }

    public HashMap<String, String> getTracking() {
        return tracking;
    }

    public void setSecondParent(String secondParent) {
        this.secondParent = secondParent;
    }

    public String getSecondParent() {
        return secondParent;
    }

}
