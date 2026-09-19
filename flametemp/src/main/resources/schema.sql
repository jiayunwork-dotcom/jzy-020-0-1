CREATE TABLE IF NOT EXISTS jobs (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    fuel             TEXT    NOT NULL,
    equivalence_ratio REAL   NOT NULL,
    inlet_temperature REAL   NOT NULL,
    status           TEXT    NOT NULL,
    final_temperature REAL,
    enthalpy_tolerance REAL,
    atom_residual_c  REAL,
    atom_residual_h  REAL,
    atom_residual_o  REAL,
    atom_residual_n  REAL,
    atom_threshold   REAL,
    reactant_enthalpy REAL,
    failure_reason   TEXT,
    failure_message  TEXT,
    created_at       TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS job_iterations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id        INTEGER NOT NULL REFERENCES jobs(id),
    step_no       INTEGER NOT NULL,
    temperature   REAL    NOT NULL,
    enthalpy_residual REAL NOT NULL,
    UNIQUE(job_id, step_no)
);

CREATE TABLE IF NOT EXISTS job_species (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id        INTEGER NOT NULL REFERENCES jobs(id),
    species       TEXT    NOT NULL,
    phase         TEXT    NOT NULL,
    moles         REAL    NOT NULL,
    mole_fraction REAL    NOT NULL,
    UNIQUE(job_id, species, phase)
);
