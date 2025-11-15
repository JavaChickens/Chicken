CREATE TABLE IF NOT EXISTS environments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    language TEXT NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('compiled', 'interpreted', 'transpiled', 'build-system', 'specialized', 'container')),
    activation_path TEXT NOT NULL,
    version TEXT,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT 0,
    is_installed BOOLEAN DEFAULT 0
);

-- Language specifications
CREATE TABLE IF NOT EXISTS language_specs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    compiler TEXT,
    interpreter TEXT,
    runtime TEXT,
    standard TEXT,
    toolchain TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- File extensions associated with environments
CREATE TABLE IF NOT EXISTS file_extensions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    extension TEXT NOT NULL,
    file_type TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Build systems
CREATE TABLE IF NOT EXISTS build_systems (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    is_primary BOOLEAN DEFAULT 1,
    config_file TEXT,
    build_command TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Package managers
CREATE TABLE IF NOT EXISTS package_managers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    is_primary BOOLEAN DEFAULT 1,
    install_command TEXT,
    update_command TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Dependencies
CREATE TABLE IF NOT EXISTS dependencies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    version TEXT,
    is_required BOOLEAN DEFAULT 1,
    install_url TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Platform support
CREATE TABLE IF NOT EXISTS platforms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    platform TEXT NOT NULL CHECK(platform IN ('Windows', 'Linux', 'macOS', 'Web', 'Bare Metal')),
    is_supported BOOLEAN DEFAULT 1,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- IDE support
CREATE TABLE IF NOT EXISTS ide_support (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    ide_name TEXT NOT NULL,
    version TEXT,
    plugin_required TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Environment paths
CREATE TABLE IF NOT EXISTS environment_paths (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    path_type TEXT NOT NULL CHECK(path_type IN ('bin', 'lib', 'include', 'src', 'build', 'cache', 'logs', 'temp')),
    path TEXT NOT NULL,
    is_relative BOOLEAN DEFAULT 1,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Environment variables
CREATE TABLE IF NOT EXISTS environment_vars (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    var_name TEXT NOT NULL,
    var_value TEXT,
    var_type TEXT CHECK(var_type IN ('PATH', 'CONFIG', 'SYSTEM')),
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

-- Activation history
CREATE TABLE IF NOT EXISTS activation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_id INTEGER NOT NULL,
    activated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deactivated_at DATETIME,
    user TEXT,
    session_id TEXT,
    FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

