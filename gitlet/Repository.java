package gitlet;
import java.io.File;
import java.util.*;

/** Represents a gitlet repository.
 *  does at a high level.
 *
 *  @author Andrew Falcon
 */
public class Repository {

    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITLET_DIR = Utils.join(CWD, ".gitlet");
    public static final File COMMIT_DIR = Utils.join(GITLET_DIR, "commits");
    public static final File BLOB_DIR = Utils.join(GITLET_DIR, "blobs");
    public static final File STAGE_DIR = Utils.join(GITLET_DIR, "stages");
    public static final int MAX_PREFIX_LENGTH = 40;

    HashMap<String, byte[]> added;
    HashMap<String, String> removed;
    HashMap<String, String> branches;

    private String head;

    public Repository() {
        if (!GITLET_DIR.exists()) {
            added = new HashMap<>();
            removed = new HashMap<>();
            branches = new HashMap<>();
        } else {
            added = Utils.readObject(Utils.join(STAGE_DIR, "added"), HashMap.class);
            removed  = Utils.readObject(Utils.join(STAGE_DIR, "removed"), HashMap.class);
            branches = Utils.readObject(Utils.join(GITLET_DIR, "branches"), HashMap.class);
            head = branches.get(branches.get("current"));
        }
    }

    public void init() {
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdirs();
            COMMIT_DIR.mkdirs();
            BLOB_DIR.mkdirs();
            STAGE_DIR.mkdirs();

            Commit initialCommit = new Commit(
                    "initial commit",
                    null,
                    new HashMap<String, String>(),
                    new HashMap<String, String>(),
                    null
            );

            head = initialCommit.getHash();

            branches.put("current", "main");
            branches.put("main", head);

            Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);
            Utils.writeObject(Utils.join(COMMIT_DIR, head), initialCommit);
            Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
            Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
        } else {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
        }
    }

    public void add(String filename) {
        if (!Utils.join(CWD, filename).exists()) {
            System.out.println("File does not exist.");
            return;
        } else if (removed.containsKey(filename)) {
            removed.remove(filename);
            Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
        }

        byte[] blob = Utils.readContents(Utils.join(CWD, filename));

        if (Utils.getCommit(head).containsFile(filename)) {
            byte[] fileFromCommit = Utils.getCommit(head).getFile(filename);
            if (Arrays.equals(blob, fileFromCommit)) {
                added.remove(filename);
                Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
                return;
            }
        }
        added.put(filename, blob);
        Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
    }

    public void commit(String message, String secondParent) {
        if (Utils.getCommit(head).getParent() == null && added.isEmpty()) {
            System.out.println("No changes added to the commit.");
        }

        HashMap<String, String> addedPointers = new HashMap<>();

        for (String filename : added.keySet()) {
            byte[] blob = added.get(filename);
            String blobHash = Utils.sha1(blob);
            Utils.writeContents(Utils.join(BLOB_DIR, blobHash), blob);
            addedPointers.put(filename, blobHash);
        }

        Commit newCommit = new Commit(message, head, addedPointers, removed, secondParent);

        String newHash = newCommit.getHash();
        Utils.writeObject(Utils.join(COMMIT_DIR, newHash), newCommit);

        branches.put(branches.get("current"), newHash);
        Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);

        clearStage();
    }

    public void rm(String filename) {
        HashMap<String, String> tracking = Utils.getCommit(head).getTracking();

        if (!added.containsKey(filename) && !tracking.containsKey(filename)) {
            System.out.println("No reason to remove the file.");
        }

        if (added.containsKey(filename)) {
            added.remove(filename);
            Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
        }

        if (tracking.containsKey(filename)) {
            removed.put(filename, tracking.get(filename));
            Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
            Utils.restrictedDelete(filename);
        }
    }

    public void log() {
        Commit curr = Utils.getCommit(head);
        while (curr != null) {
            System.out.println("===");
            System.out.println("commit " + curr.getHash());
            System.out.println("Date: " + curr.getTimestamp());
            System.out.println(curr.getMessage());
            System.out.println();

            curr = Utils.getCommit(curr.getParent());
        }
    }

    public void globalLog() {
        List<String> commits = Utils.plainFilenamesIn(COMMIT_DIR);
        for (String commitHash : commits) {
            System.out.println("===");
            System.out.println("commit " + commitHash);
            System.out.println("Date: " + Utils.getCommit(commitHash).getTimestamp());
            System.out.println(Utils.getCommit(commitHash).getMessage());
            System.out.println();
        }
    }

    public void status() {
        System.out.println("=== Branches ===");
        ArrayList<String> sortedBranches = new ArrayList<>(branches.keySet());
        Collections.sort(sortedBranches);
        for (String branch : sortedBranches) {
            if (!branch.equals("current")) {
                if (branch.equals("main")) {
                    System.out.println("*main");
                } else {
                    System.out.println(branch);
                }
            }
        }
        System.out.println();

        System.out.println("=== Staged Files ===");
        ArrayList<String> sortedAdded = new ArrayList<>(added.keySet());
        Collections.sort(sortedAdded);
        for (String filename : sortedAdded) {
            System.out.println(filename);
        }
        System.out.println();

        System.out.println("=== Removed Files ===");
        ArrayList<String> sortedRemoved = new ArrayList<>(removed.keySet());
        Collections.sort(sortedRemoved);
        for (String filename : sortedRemoved) {
            System.out.println(filename);
        }
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();

        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    public void restore(String filename) {
        Commit curr = Utils.getCommit(head);
        if (curr.containsFile(filename)) {
            Utils.writeContents(Utils.join(CWD, filename), curr.getFile(filename));
        } else {
            System.out.println("File does not exist in that commit.");
        }
    }

    public void restore(String commitHash, String filename) {
        List<String> cwd = Utils.plainFilenamesIn(COMMIT_DIR);
        if (isPrefix(commitHash, cwd)) {
            Commit curr = Utils.getCommit(findStringWithPrefix(commitHash, cwd));
            if (curr.getTracking().containsKey(filename)) {
                Utils.writeContents(Utils.join(CWD, filename), curr.getFile(filename));
            } else {
                System.out.println("File does not exist in that commit.");
            }
        } else {
            System.out.println("No commit with that id exists.");
        }
    }

    public void find(String message) {
        List<String> commits = Utils.plainFilenamesIn(COMMIT_DIR);
        int count = 0;
        for (String commitHash : commits) {
            Commit curr = Utils.getCommit(commitHash);
            if (curr.getMessage().equals(message)) {
                System.out.println(commitHash);
                count++;
            }
        }
        if (count == 0) {
            System.out.println("Found no commit with that message.");
        }
    }

    public void branch(String branchName) {
        if (branches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
        } else {
            branches.put(branchName, branches.get(branches.get("current")));
            Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);
        }
    }

    public void switchBranch(String branchName) {
        if (!branches.containsKey(branchName)) {
            System.out.println("No such branch exists.");
            return;
        } else if (branches.get("current").equals(branchName)) {
            System.out.println("No need to switch to the current branch.");
            return;
        }

        Commit currCommit = Utils.getCommit(head);
        Commit branchCommit = Utils.getCommit(branches.get(branchName));
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);

        for (String filename : cwdFiles) {
            if (!currCommit.getTracking().containsKey(filename) && branchCommit.getTracking().containsKey(filename)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        for (String filename : branchCommit.getTracking().keySet()) {
            Utils.writeContents(Utils.join(CWD, filename), branchCommit.getFile(filename));
        }

        // Do delete separately
        for (String filename : currCommit.getTracking().keySet()) {
            if (!branchCommit.getTracking().keySet().contains(filename)) {
                Utils.restrictedDelete(Utils.join(CWD, filename));
            }
        }

        branches.put("current", branchName);
        Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);
        clearStage();
    }

    public void rmBranch(String branchName) {
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        } else if (branches.get("current").equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }

        branches.remove(branchName);
        Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);
    }

    public void reset(String commitHash) {
        if (!Utils.plainFilenamesIn(COMMIT_DIR).contains(commitHash)) {
            System.out.println("No commit with that id exists.");
            return;
        }

        Commit currCommit = Utils.getCommit(head);
        Commit branchCommit = Utils.getCommit(commitHash);
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);

        for (String filename : cwdFiles) {
            if (!currCommit.getTracking().containsKey(filename) && branchCommit.getTracking().containsKey(filename)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        for (String filename : branchCommit.getTracking().keySet()) {
            Utils.writeContents(Utils.join(CWD, filename), branchCommit.getFile(filename));
        }

        // Do delete separately
        for (String filename : currCommit.getTracking().keySet()) {
            if (!branchCommit.getTracking().keySet().contains(filename)) {
                Utils.restrictedDelete(Utils.join(CWD, filename));
            }
        }

        branches.put(branches.get("current"), commitHash);
        Utils.writeObject(Utils.join(GITLET_DIR, "branches"), branches);
        clearStage();

    }

    public void merge(String given) {
        if (mergeFailureCases(given)) {
            return;
        }
        Commit splitCommit = findSplit(given);
        Commit currentCommit = Utils.getCommit(head);
        Commit givenCommit = Utils.getCommit(branches.get(given));
        Set<String> splitFiles = splitCommit.getTracking().keySet();
        Set<String> currentFiles = currentCommit.getTracking().keySet();
        Set<String> givenFiles = givenCommit.getTracking().keySet();
        Set<String> files = new HashSet<>();
        files.addAll(splitFiles);
        files.addAll(currentFiles);
        files.addAll(givenFiles);
        if (splitCommit.getHash().equals(givenCommit.getHash())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        } else if (splitCommit.getHash().equals(currentCommit.getHash())) {
            switchBranch(given);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        for (String filename : files) {
            boolean conflictCaseOne = isModified(filename, currentCommit, splitCommit)
                    && isModified(filename, givenCommit, splitCommit)
                    && !isSameContent(filename, givenCommit, currentCommit);
            boolean conflictCaseTwo = !isContained(filename, splitCommit)
                    && isContained(filename, currentCommit)
                    && isContained(filename, givenCommit)
                    && !isSameContent(filename, currentCommit, givenCommit);
            if (!isModified(filename, currentCommit, splitCommit) && isModified(filename, givenCommit, splitCommit)) {
                if (isContained(filename, splitCommit) && !isContained(filename, givenCommit)) {
                    removed.put(filename, currentCommit.getTracking().get(filename));
                    Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
                    Utils.restrictedDelete(filename);
                } else {
                    Utils.writeContents(Utils.join(CWD, filename), givenCommit.getFile(filename));
                    added.put(filename, Utils.readContents(Utils.join(CWD, filename)));
                    Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
                }
            } else if (!isContained(filename, splitCommit)
                    && !isContained(filename, currentCommit)
                    && isContained(filename, givenCommit)) {
                Utils.writeContents(Utils.join(CWD, filename), givenCommit.getFile(filename));
                added.put(filename, Utils.readContents(Utils.join(CWD, filename)));
                Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
            } else if (conflictCaseOne || conflictCaseTwo) {
                System.out.println("Encountered a merge conflict.");
                byte[] currentContents = new byte[0];
                byte[] givenContents = new byte[0];
                if (!isContained(filename, givenCommit) && isContained(filename, currentCommit)) {
                    givenContents = new byte[0];
                    currentContents = currentCommit.getFile(filename);
                } else if (!isContained(filename, currentCommit) && isContained(filename, givenCommit)) {
                    currentContents = new byte[0];
                    givenContents = givenCommit.getFile(filename);
                } else {
                    currentContents = currentCommit.getFile(filename);
                    givenContents = givenCommit.getFile(filename);
                }
                Utils.writeContents(Utils.join(CWD, filename),
                        "<<<<<<< HEAD\n", currentContents, "=======\n", givenContents, ">>>>>>>\n");
            } else if (isContained(filename, splitCommit)
                    && isContained(filename, currentCommit)
                    && !isModified(filename, currentCommit, splitCommit)
                    && !isContained(filename, givenCommit)) {
                removed.put(filename, currentCommit.getTracking().get(filename));
                Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
                Utils.restrictedDelete(filename);
            }
        }
        commit(String.format("Merged %s into %s.", given, branches.get("current")), branches.get(given));
    }

    public boolean willBeOverridden(String given) {
        Commit currentCommit = Utils.getCommit(head);
        Commit givenCommit = Utils.getCommit(branches.get(given));

        Set<String> currentFiles = currentCommit.getTracking().keySet();
        Set<String> givenFiles = givenCommit.getTracking().keySet();

        for (String filename : givenFiles) {
            if (Utils.plainFilenamesIn(CWD).contains(filename) && !currentFiles.contains(filename)) {
                return true;
            }
        }

        return false;
    }

    public boolean mergeFailureCases(String branch) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return true;
        } else if (!branches.containsKey(branch)) {
            System.out.println("A branch with that name does not exist.");
            return true;
        } else if (branch.equals(branches.get("current"))) {
            System.out.println("Cannot merge a branch with itself.");
            return true;
        } else if (willBeOverridden(branch)) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            return true;
        }
        return false;
    }

    public Commit findSplit(String branchName) {
        Set<String> ancestors = new HashSet<>();
        Commit curr = Utils.getCommit(head);
        Commit other = Utils.getCommit(branches.get(branchName));

        Queue<Commit> queue = new LinkedList<>();
        queue.offer(curr);
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            if (!ancestors.add(commit.getHash())) {
                continue;
            }
            if (commit.getParent() != null) {
                queue.offer(Utils.getCommit(commit.getParent()));
            }
            if (commit.getSecondParent() != null) {
                queue.offer(Utils.getCommit(commit.getSecondParent()));
            }
        }

        queue.clear();
        queue.offer(other);
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            if (!visited.add(commit.getHash())) {
                continue;
            }
            if (ancestors.contains(commit.getHash())) {
                return commit;
            }
            if (commit.getParent() != null) {
                queue.offer(Utils.getCommit(commit.getParent()));
            }
            if (commit.getSecondParent() != null) {
                queue.offer(Utils.getCommit(commit.getSecondParent()));
            }
        }

        return null;
    }

    public boolean isModified(String filename, Commit curr, Commit splitPoint) {
        // if file has been added it HAS NOT been modified
        if (!isContained(filename, splitPoint) && isContained(filename, curr)) {
            return false;
        } else if (isContained(filename, splitPoint) && !isContained(filename, curr)) {
            return true; // files that have been removed HAVE been modified
        } else if (isContained(filename, splitPoint) && isContained(filename, curr)) {
            byte[] splitFile = splitPoint.getFile(filename);
            byte[] currFile = curr.getFile(filename);

            return !Arrays.equals(splitFile, currFile);
        }
        return false; // this will never be called (case where file does not exist in either)
    }

    public boolean isContained(String filename, Commit curr) {
        return curr.getTracking().containsKey(filename);
    }

    public boolean isSameContent(String filename, Commit commitOne, Commit commitTwo) {
        byte[] first = commitOne.getFile(filename);
        byte[] second = commitTwo.getFile(filename);

        return Arrays.equals(first, second);
    }

    public void clearStage() {
        added.clear();
        removed.clear();
        Utils.writeObject(Utils.join(STAGE_DIR, "added"), added);
        Utils.writeObject(Utils.join(STAGE_DIR, "removed"), removed);
    }

    public boolean inInitializedRepository() {
        return GITLET_DIR.exists();
    }

    public static List<String> getPrefixes(String input) {
        List<String> prefixes = new ArrayList<>();
        int maxLength = Math.min(input.length(), MAX_PREFIX_LENGTH);

        for (int i = 1; i <= maxLength; i++) {
            prefixes.add(input.substring(0, i));
        }

        return prefixes;
    }

    public static boolean isPrefix(String prefix, List<String> stringList) {
        for (String str : stringList) {
            if (str.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String findStringWithPrefix(String prefix, List<String> stringList) {
        for (String str : stringList) {
            if (str.startsWith(prefix)) {
                return str;
            }
        }
        return null;
    }

}
