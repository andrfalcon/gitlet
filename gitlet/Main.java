package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Andrew Falcon
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */

    public static void main(String[] args) {
        Repository repo = new Repository();
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        if (firstArg.equals("init")) {
            repo.init();
        } else {
            if (repo.inInitializedRepository()) {
                switch (firstArg) {
                    case "init":
                        repo.init();
                        break;
                    case "add":
                        repo.add(args[1]);
                        break;
                    case "commit":
                        if (args[1].equals("")) {
                            System.out.println("Please enter a commit message.");
                            break;
                        }
                        repo.commit(args[1], null);
                        break;
                    case "restore":
                        if (args[1].equals("--")) {
                            repo.restore(args[2]);
                        } else if (!args[2].equals("--")) {
                            System.out.println("Incorrect operands.");
                        } else {
                            repo.restore(args[1], args[3]);
                        }
                        break;
                    case "log":
                        repo.log();
                        break;
                    case "status":
                        repo.status();
                        break;
                    case "rm":
                        repo.rm(args[1]);
                        break;
                    case "global-log":
                        repo.globalLog();
                        break;
                    case "find":
                        repo.find(args[1]);
                        break;
                    case "branch":
                        repo.branch(args[1]);
                        break;
                    case "switch":
                        repo.switchBranch(args[1]);
                        break;
                    case "rm-branch":
                        repo.rmBranch(args[1]);
                        break;
                    case "reset":
                        repo.reset(args[1]);
                        break;
                    case "merge":
                        repo.merge(args[1]);
                        break;
                    default:
                        System.out.println("No command with that name exists.");
                        break;
                }
            } else {
                System.out.println("Not in an initialized Gitlet directory.");
            }
        }
    }
}
