import java.util.*;

// ==================== COMPOSITE PATTERN ====================
// Definition: Composes objects into tree structures to represent part-whole hierarchies.
//
// REAL System Design Interview Examples:
// 1. File System / Object Storage - S3 folders, directories
// 2. Organization Permissions - Multi-level access control
// 3. Service Mesh / API Routes - Nested route hierarchies
// 4. Cloud Resource Groups - AWS, Azure resource organization
// 5. Multi-Tenant Systems - Tenant → Organization → Team hierarchy
//
// Interview Use Cases:
// - Design File Storage: Nested folders with size calculations
// - Design Permission System: Hierarchical roles and permissions
// - Design API Gateway: Nested route groups with middleware
// - Design Cloud Platform: Resource groups with cost tracking
// - Design SaaS Platform: Multi-tenant hierarchy

// ==================== EXAMPLE 1: DISTRIBUTED FILE SYSTEM ====================
// Used in: S3, GCS, HDFS, Dropbox
// Interview Question: Design a file storage system like Dropbox/Google Drive

interface FileSystemNode {
    String getName();
    long getSize();
    int getFileCount();
    void display(String indent);
    String getPath();
}

class StorageFile implements FileSystemNode {
    private String name;
    private long sizeBytes;
    private String storageLocation; // S3 key, GCS object path, etc.
    private String parentPath;

    public StorageFile(String name, long sizeBytes, String parentPath, String storageLocation) {
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.parentPath = parentPath;
        this.storageLocation = storageLocation;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return sizeBytes;
    }

    @Override
    public int getFileCount() {
        return 1;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s📄 %s (%s) [%s]%n", 
            indent, name, formatSize(sizeBytes), storageLocation);
    }

    @Override
    public String getPath() {
        return parentPath + "/" + name;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

class StorageFolder implements FileSystemNode {
    private String name;
    private String path;
    private List<FileSystemNode> children;
    private String ownerId;

    public StorageFolder(String name, String path, String ownerId) {
        this.name = name;
        this.path = path;
        this.children = new ArrayList<>();
        this.ownerId = ownerId;
    }

    public void add(FileSystemNode node) {
        children.add(node);
    }

    public void remove(FileSystemNode node) {
        children.remove(node);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return children.stream()
            .mapToLong(FileSystemNode::getSize)
            .sum();
    }

    @Override
    public int getFileCount() {
        return children.stream()
            .mapToInt(FileSystemNode::getFileCount)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s📁 %s/ (%d files, %s) [Owner: %s]%n", 
            indent, name, getFileCount(), formatSize(getSize()), ownerId);
        for (FileSystemNode child : children) {
            child.display(indent + "  ");
        }
    }

    @Override
    public String getPath() {
        return path;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

// ==================== EXAMPLE 2: PERMISSION SYSTEM ====================
// Used in: RBAC, AWS IAM, Google Cloud IAM
// Interview Question: Design a role-based access control system

interface PermissionNode {
    boolean hasPermission(String permission);
    Set<String> getAllPermissions();
    void display(String indent);
}

class Permission implements PermissionNode {
    private String name;
    private String resource;
    private String action; // read, write, delete, admin

    public Permission(String name, String resource, String action) {
        this.name = name;
        this.resource = resource;
        this.action = action;
    }

    @Override
    public boolean hasPermission(String permission) {
        return (resource + ":" + action).equals(permission);
    }

    @Override
    public Set<String> getAllPermissions() {
        Set<String> perms = new HashSet<>();
        perms.add(resource + ":" + action);
        return perms;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s🔑 %s (%s:%s)%n", indent, name, resource, action);
    }
}

class Role implements PermissionNode {
    private String roleName;
    private List<PermissionNode> permissions;

    public Role(String roleName) {
        this.roleName = roleName;
        this.permissions = new ArrayList<>();
    }

    public void add(PermissionNode permission) {
        permissions.add(permission);
    }

    @Override
    public boolean hasPermission(String permission) {
        for (PermissionNode perm : permissions) {
            if (perm.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getAllPermissions() {
        Set<String> allPerms = new HashSet<>();
        for (PermissionNode perm : permissions) {
            allPerms.addAll(perm.getAllPermissions());
        }
        return allPerms;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s👤 Role: %s (%d permissions)%n", 
            indent, roleName, getAllPermissions().size());
        for (PermissionNode perm : permissions) {
            perm.display(indent + "  ");
        }
    }
}

// ==================== EXAMPLE 3: API GATEWAY ROUTE HIERARCHY ====================
// Used in: Kong, AWS API Gateway, Nginx
// Interview Question: Design an API Gateway with nested routes

interface RouteNode {
    boolean matches(String path);
    String getRoutePath();
    void display(String indent);
}

class Endpoint implements RouteNode {
    private String path;
    private String method;
    private String handler;

    public Endpoint(String path, String method, String handler) {
        this.path = path;
        this.method = method;
        this.handler = handler;
    }

    @Override
    public boolean matches(String path) {
        return this.path.equals(path);
    }

    @Override
    public String getRoutePath() {
        return path;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s➤ %s %s → %s%n", indent, method, path, handler);
    }
}

class RouteGroup implements RouteNode {
    private String basePath;
    private List<RouteNode> routes;
    private List<String> middleware;

    public RouteGroup(String basePath) {
        this.basePath = basePath;
        this.routes = new ArrayList<>();
        this.middleware = new ArrayList<>();
    }

    public void add(RouteNode route) {
        routes.add(route);
    }

    public void addMiddleware(String middlewareName) {
        middleware.add(middlewareName);
    }

    @Override
    public boolean matches(String path) {
        return path.startsWith(basePath);
    }

    @Override
    public String getRoutePath() {
        return basePath;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s📂 %s [Middleware: %s]%n", 
            indent, basePath, middleware.isEmpty() ? "none" : String.join(", ", middleware));
        for (RouteNode route : routes) {
            route.display(indent + "  ");
        }
    }
}

// ==================== EXAMPLE 4: CLOUD RESOURCE HIERARCHY ====================
// Used in: AWS, Azure, GCP resource organization
// Interview Question: Design a cloud resource management system

interface CloudResource {
    String getName();
    double getCost();
    int getResourceCount();
    void display(String indent);
}

class ComputeInstance implements CloudResource {
    private String instanceId;
    private String instanceType;
    private double hourlyCost;

    public ComputeInstance(String instanceId, String instanceType, double hourlyCost) {
        this.instanceId = instanceId;
        this.instanceType = instanceType;
        this.hourlyCost = hourlyCost;
    }

    @Override
    public String getName() {
        return instanceId;
    }

    @Override
    public double getCost() {
        return hourlyCost * 24 * 30; // Monthly cost
    }

    @Override
    public int getResourceCount() {
        return 1;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s🖥️ EC2: %s (%s) - $%.2f/month%n", 
            indent, instanceId, instanceType, getCost());
    }
}

class ResourceGroup implements CloudResource {
    private String groupName;
    private List<CloudResource> resources;
    private String region;

    public ResourceGroup(String groupName, String region) {
        this.groupName = groupName;
        this.region = region;
        this.resources = new ArrayList<>();
    }

    public void add(CloudResource resource) {
        resources.add(resource);
    }

    @Override
    public String getName() {
        return groupName;
    }

    @Override
    public double getCost() {
        return resources.stream()
            .mapToDouble(CloudResource::getCost)
            .sum();
    }

    @Override
    public int getResourceCount() {
        return resources.stream()
            .mapToInt(CloudResource::getResourceCount)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s📦 ResourceGroup: %s [%s] (%d resources, $%.2f/month)%n", 
            indent, groupName, region, getResourceCount(), getCost());
        for (CloudResource resource : resources) {
            resource.display(indent + "  ");
        }
    }
}

// ==================== EXAMPLE 5: MULTI-TENANT SAAS HIERARCHY ====================
// Used in: Salesforce, Slack, SaaS platforms
// Interview Question: Design a multi-tenant SaaS application

interface TenantNode {
    String getId();
    int getUserCount();
    double getUsage();
    void display(String indent);
}

class User implements TenantNode {
    private String userId;
    private String email;
    private double storageUsageGB;

    public User(String userId, String email, double storageUsageGB) {
        this.userId = userId;
        this.email = email;
        this.storageUsageGB = storageUsageGB;
    }

    @Override
    public String getId() {
        return userId;
    }

    @Override
    public int getUserCount() {
        return 1;
    }

    @Override
    public double getUsage() {
        return storageUsageGB;
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s👤 %s (%s) - %.2f GB%n", indent, userId, email, storageUsageGB);
    }
}

class Team implements TenantNode {
    private String teamId;
    private String teamName;
    private List<TenantNode> members;

    public Team(String teamId, String teamName) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.members = new ArrayList<>();
    }

    public void addMember(TenantNode member) {
        members.add(member);
    }

    @Override
    public String getId() {
        return teamId;
    }

    @Override
    public int getUserCount() {
        return members.stream()
            .mapToInt(TenantNode::getUserCount)
            .sum();
    }

    @Override
    public double getUsage() {
        return members.stream()
            .mapToDouble(TenantNode::getUsage)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s👥 Team: %s (%d users, %.2f GB)%n", 
            indent, teamName, getUserCount(), getUsage());
        for (TenantNode member : members) {
            member.display(indent + "  ");
        }
    }
}

class Organization implements TenantNode {
    private String orgId;
    private String orgName;
    private List<TenantNode> teams;
    private String plan;

    public Organization(String orgId, String orgName, String plan) {
        this.orgId = orgId;
        this.orgName = orgName;
        this.plan = plan;
        this.teams = new ArrayList<>();
    }

    public void addTeam(TenantNode team) {
        teams.add(team);
    }

    @Override
    public String getId() {
        return orgId;
    }

    @Override
    public int getUserCount() {
        return teams.stream()
            .mapToInt(TenantNode::getUserCount)
            .sum();
    }

    @Override
    public double getUsage() {
        return teams.stream()
            .mapToDouble(TenantNode::getUsage)
            .sum();
    }

    @Override
    public void display(String indent) {
        System.out.printf("%s🏢 Organization: %s [%s Plan] (%d users, %.2f GB total)%n", 
            indent, orgName, plan, getUserCount(), getUsage());
        for (TenantNode team : teams) {
            team.display(indent + "  ");
        }
    }
}

// ==================== DEMO ====================

public class CompositePattern {
    public static void main(String[] args) {
        System.out.println("========== COMPOSITE PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DISTRIBUTED FILE SYSTEM =====
        System.out.println("===== EXAMPLE 1: DISTRIBUTED FILE SYSTEM (S3/GCS/Dropbox) =====\n");
        
        StorageFolder root = new StorageFolder("my-bucket", "/", "user-123");
        
        StorageFolder images = new StorageFolder("images", "/images", "user-123");
        images.add(new StorageFile("photo1.jpg", 1024 * 1024 * 3, "/images", "s3://bucket/images/photo1.jpg"));
        images.add(new StorageFile("photo2.jpg", 1024 * 1024 * 2, "/images", "s3://bucket/images/photo2.jpg"));
        
        StorageFolder documents = new StorageFolder("documents", "/documents", "user-123");
        documents.add(new StorageFile("report.pdf", 1024 * 500, "/documents", "s3://bucket/documents/report.pdf"));
        
        StorageFolder projects = new StorageFolder("projects", "/documents/projects", "user-123");
        projects.add(new StorageFile("design.sketch", 1024 * 1024 * 10, "/documents/projects", "s3://bucket/documents/projects/design.sketch"));
        documents.add(projects);
        
        root.add(images);
        root.add(documents);
        root.add(new StorageFile("readme.txt", 500, "/", "s3://bucket/readme.txt"));
        
        root.display("");
        System.out.printf("%nTotal storage: %d files, %.2f MB%n", 
            root.getFileCount(), root.getSize() / (1024.0 * 1024));

        // ===== EXAMPLE 2: PERMISSION SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 2: HIERARCHICAL PERMISSIONS (AWS IAM/RBAC) =====\n");
        
        Role adminRole = new Role("admin");
        adminRole.add(new Permission("read-users", "users", "read"));
        adminRole.add(new Permission("write-users", "users", "write"));
        adminRole.add(new Permission("delete-users", "users", "delete"));
        
        Role editorRole = new Role("editor");
        editorRole.add(new Permission("read-content", "content", "read"));
        editorRole.add(new Permission("write-content", "content", "write"));
        
        Role superAdminRole = new Role("super-admin");
        superAdminRole.add(adminRole); // Inherit all admin permissions
        superAdminRole.add(editorRole); // Plus all editor permissions
        superAdminRole.add(new Permission("system-config", "system", "admin"));
        
        System.out.println("Super Admin Role Hierarchy:");
        superAdminRole.display("");
        
        System.out.println("\nPermission Check:");
        System.out.println("Has 'users:write'? " + superAdminRole.hasPermission("users:write"));
        System.out.println("Has 'content:read'? " + superAdminRole.hasPermission("content:read"));
        System.out.println("Has 'users:admin'? " + superAdminRole.hasPermission("users:admin"));
        System.out.println("\nTotal permissions: " + superAdminRole.getAllPermissions().size());

        // ===== EXAMPLE 3: API GATEWAY ROUTES =====
        System.out.println("\n\n===== EXAMPLE 3: API GATEWAY ROUTES (Kong/AWS API Gateway) =====\n");
        
        RouteGroup api = new RouteGroup("/api");
        api.addMiddleware("authentication");
        api.addMiddleware("rate-limiting");
        
        RouteGroup v1 = new RouteGroup("/api/v1");
        v1.addMiddleware("versioning");
        v1.add(new Endpoint("/api/v1/users", "GET", "UserController.list"));
        v1.add(new Endpoint("/api/v1/users", "POST", "UserController.create"));
        v1.add(new Endpoint("/api/v1/users/:id", "GET", "UserController.get"));
        
        RouteGroup v2 = new RouteGroup("/api/v2");
        v2.addMiddleware("versioning");
        v2.addMiddleware("schema-validation");
        v2.add(new Endpoint("/api/v2/users", "GET", "UserControllerV2.list"));
        v2.add(new Endpoint("/api/v2/orders", "GET", "OrderController.list"));
        
        api.add(v1);
        api.add(v2);
        
        System.out.println("API Gateway Route Hierarchy:");
        api.display("");

        // ===== EXAMPLE 4: CLOUD RESOURCE GROUPS =====
        System.out.println("\n\n===== EXAMPLE 4: CLOUD RESOURCE GROUPS (AWS/Azure) =====\n");
        
        ResourceGroup production = new ResourceGroup("production-resources", "us-east-1");
        
        ResourceGroup webTier = new ResourceGroup("web-tier", "us-east-1");
        webTier.add(new ComputeInstance("i-web-001", "t3.medium", 0.0416));
        webTier.add(new ComputeInstance("i-web-002", "t3.medium", 0.0416));
        webTier.add(new ComputeInstance("i-web-003", "t3.medium", 0.0416));
        
        ResourceGroup appTier = new ResourceGroup("app-tier", "us-east-1");
        appTier.add(new ComputeInstance("i-app-001", "t3.large", 0.0832));
        appTier.add(new ComputeInstance("i-app-002", "t3.large", 0.0832));
        
        ResourceGroup dataTier = new ResourceGroup("data-tier", "us-east-1");
        dataTier.add(new ComputeInstance("i-db-001", "r5.xlarge", 0.252));
        dataTier.add(new ComputeInstance("i-db-002", "r5.xlarge", 0.252));
        
        production.add(webTier);
        production.add(appTier);
        production.add(dataTier);
        
        System.out.println("Cloud Resource Hierarchy:");
        production.display("");
        System.out.printf("%nTotal monthly cost: $%.2f%n", production.getCost());

        // ===== EXAMPLE 5: MULTI-TENANT SAAS =====
        System.out.println("\n\n===== EXAMPLE 5: MULTI-TENANT SAAS PLATFORM (Salesforce/Slack) =====\n");
        
        Organization acmeCorp = new Organization("org-001", "ACME Corporation", "Enterprise");
        
        Team engineering = new Team("team-eng", "Engineering");
        engineering.addMember(new User("user-001", "alice@acme.com", 5.5));
        engineering.addMember(new User("user-002", "bob@acme.com", 3.2));
        engineering.addMember(new User("user-003", "charlie@acme.com", 4.8));
        
        Team marketing = new Team("team-mkt", "Marketing");
        marketing.addMember(new User("user-004", "david@acme.com", 2.1));
        marketing.addMember(new User("user-005", "emma@acme.com", 1.9));
        
        Team design = new Team("team-design", "Design");
        design.addMember(new User("user-006", "frank@acme.com", 15.5)); // Designer with large files
        design.addMember(new User("user-007", "grace@acme.com", 12.3));
        
        acmeCorp.addTeam(engineering);
        acmeCorp.addTeam(marketing);
        acmeCorp.addTeam(design);
        
        System.out.println("SaaS Tenant Hierarchy:");
        acmeCorp.display("");
        
        System.out.printf("%nOrganization Summary:%n");
        System.out.printf("  Total users: %d%n", acmeCorp.getUserCount());
        System.out.printf("  Total storage: %.2f GB%n", acmeCorp.getUsage());
        System.out.printf("  Average per user: %.2f GB%n", acmeCorp.getUsage() / acmeCorp.getUserCount());

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• File storage systems (S3, GCS, Dropbox, Google Drive)");
        System.out.println("• Permission systems (AWS IAM, RBAC, Azure AD)");
        System.out.println("• API gateways (Kong, AWS API Gateway, route hierarchies)");
        System.out.println("• Cloud resource management (AWS, Azure, GCP)");
        System.out.println("• Multi-tenant SaaS platforms (Salesforce, Slack, Teams)");
    }
}
