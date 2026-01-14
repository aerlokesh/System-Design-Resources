import java.util.*;

/**
 * INTERVIEW-READY File System
 * Time to complete: 45-60 minutes
 * Focus: Tree structure, path resolution, composite pattern
 */

// ==================== File System Entry (Composite Pattern) ====================
abstract class FileSystemEntry {
    protected final String name;
    protected final FileSystemEntry parent;

    public FileSystemEntry(String name, FileSystemEntry parent) {
        this.name = name;
        this.parent = parent;
    }

    public abstract int getSize();
    public abstract boolean isDirectory();
    public abstract void display(int level);

    public String getName() { return name; }
    public FileSystemEntry getParent() { return parent; }

    public String getPath() {
        if (parent == null) {
            return "/";
        }
        String parentPath = parent.getPath();
        return parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
    }
}

// ==================== File ====================
class File extends FileSystemEntry {
    private final int size;
    private String content;

    public File(String name, FileSystemEntry parent, int size) {
        super(name, parent);
        this.size = size;
        this.content = "";
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public void display(int level) {
        String indent = "  ".repeat(level);
        System.out.println(indent + "📄 " + name + " (" + size + " bytes)");
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}

// ==================== Directory ====================
class Directory extends FileSystemEntry {
    private final Map<String, FileSystemEntry> children;

    public Directory(String name, FileSystemEntry parent) {
        super(name, parent);
        this.children = new HashMap<>();
    }

    public void addEntry(FileSystemEntry entry) {
        children.put(entry.getName(), entry);
    }

    public void removeEntry(String name) {
        children.remove(name);
    }

    public FileSystemEntry getEntry(String name) {
        return children.get(name);
    }

    public Collection<FileSystemEntry> getChildren() {
        return children.values();
    }

    @Override
    public int getSize() {
        int totalSize = 0;
        for (FileSystemEntry entry : children.values()) {
            totalSize += entry.getSize();
        }
        return totalSize;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public void display(int level) {
        String indent = "  ".repeat(level);
        System.out.println(indent + "📁 " + name + "/");
        
        // Display children
        for (FileSystemEntry entry : children.values()) {
            entry.display(level + 1);
        }
    }
}

// ==================== File System ====================
class FileSystem {
    private final Directory root;

    public FileSystem() {
        this.root = new Directory("root", null);
    }

    public File createFile(String path, int size) {
        String[] parts = parsePath(path);
        String fileName = parts[parts.length - 1];
        
        Directory parent = navigateToDirectory(parts, parts.length - 1);
        if (parent == null) {
            System.out.println("✗ Parent directory not found: " + path);
            return null;
        }

        File file = new File(fileName, parent, size);
        parent.addEntry(file);
        
        System.out.println("✓ Created file: " + path);
        return file;
    }

    public Directory createDirectory(String path) {
        String[] parts = parsePath(path);
        String dirName = parts[parts.length - 1];
        
        Directory parent = navigateToDirectory(parts, parts.length - 1);
        if (parent == null) {
            System.out.println("✗ Parent directory not found: " + path);
            return null;
        }

        Directory dir = new Directory(dirName, parent);
        parent.addEntry(dir);
        
        System.out.println("✓ Created directory: " + path);
        return dir;
    }

    public FileSystemEntry get(String path) {
        if (path.equals("/")) {
            return root;
        }

        String[] parts = parsePath(path);
        Directory current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            FileSystemEntry entry = current.getEntry(parts[i]);
            if (entry == null || !entry.isDirectory()) {
                return null;
            }
            current = (Directory) entry;
        }

        return current.getEntry(parts[parts.length - 1]);
    }

    public boolean delete(String path) {
        if (path.equals("/")) {
            System.out.println("✗ Cannot delete root");
            return false;
        }

        String[] parts = parsePath(path);
        String name = parts[parts.length - 1];
        
        Directory parent = navigateToDirectory(parts, parts.length - 1);
        if (parent == null) {
            System.out.println("✗ Path not found: " + path);
            return false;
        }

        parent.removeEntry(name);
        System.out.println("✓ Deleted: " + path);
        return true;
    }

    public List<FileSystemEntry> search(String name) {
        List<FileSystemEntry> results = new ArrayList<>();
        searchRecursive(root, name, results);
        return results;
    }

    private void searchRecursive(FileSystemEntry entry, String name, List<FileSystemEntry> results) {
        if (entry.getName().contains(name)) {
            results.add(entry);
        }

        if (entry.isDirectory()) {
            Directory dir = (Directory) entry;
            for (FileSystemEntry child : dir.getChildren()) {
                searchRecursive(child, name, results);
            }
        }
    }

    private String[] parsePath(String path) {
        if (path.equals("/")) {
            return new String[0];
        }
        
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.split("/");
    }

    private Directory navigateToDirectory(String[] parts, int endIndex) {
        Directory current = root;

        for (int i = 0; i < endIndex; i++) {
            FileSystemEntry entry = current.getEntry(parts[i]);
            if (entry == null || !entry.isDirectory()) {
                return null;
            }
            current = (Directory) entry;
        }

        return current;
    }

    public void displayTree() {
        System.out.println("\n=== File System Tree ===");
        root.display(0);
        System.out.println();
    }
}

// ==================== Demo ====================
public class FileSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== File System Demo ===\n");

        FileSystem fs = new FileSystem();

        // Create directory structure
        System.out.println("--- Creating Directories ---");
        fs.createDirectory("/home");
        fs.createDirectory("/home/user");
        fs.createDirectory("/home/user/documents");
        fs.createDirectory("/home/user/pictures");
        fs.createDirectory("/var");
        fs.createDirectory("/var/log");

        // Create files
        System.out.println("\n--- Creating Files ---");
        fs.createFile("/home/user/documents/resume.pdf", 2048);
        fs.createFile("/home/user/documents/cover_letter.doc", 1024);
        fs.createFile("/home/user/pictures/photo1.jpg", 5120);
        fs.createFile("/var/log/system.log", 10240);

        // Display tree
        fs.displayTree();

        // Get file
        System.out.println("--- Getting File ---");
        FileSystemEntry entry = fs.get("/home/user/documents/resume.pdf");
        if (entry != null) {
            System.out.println("Found: " + entry.getName() + " at " + entry.getPath());
            System.out.println("Size: " + entry.getSize() + " bytes");
        }

        // Search
        System.out.println("\n--- Searching for 'log' ---");
        List<FileSystemEntry> results = fs.search("log");
        System.out.println("Found " + results.size() + " results:");
        for (FileSystemEntry result : results) {
            System.out.println("  " + result.getPath() + " (" + result.getSize() + " bytes)");
        }

        // Delete
        System.out.println("\n--- Deleting File ---");
        fs.delete("/home/user/pictures/photo1.jpg");

        fs.displayTree();

        // Get directory size
        System.out.println("--- Directory Size ---");
        FileSystemEntry homeDir = fs.get("/home");
        if (homeDir != null) {
            System.out.println("/home total size: " + homeDir.getSize() + " bytes");
        }

        System.out.println("\n✅ Demo complete!");
    }
}
