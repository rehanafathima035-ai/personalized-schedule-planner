-- Personalized Life Planner - MySQL 8 schema
--
-- Design notes:
--   * Recurrence is stored as a RULE, never as thousands of pre-generated
--     rows. Concrete occurrences are derived at read time and only the
--     exceptions (completions, skips, reschedules) are persisted.
--   * Proposed schedules live in their own tables and are copied into
--     scheduled_item only on approval, so nothing the engine invents can
--     leak into the real plan.
--   * Every user-owned table carries user_id so authorization is a simple,
--     always-applied predicate rather than a join people forget.

CREATE DATABASE IF NOT EXISTS life_planner
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE life_planner;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  display_name    VARCHAR(120) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,   -- BCrypt. Never plaintext.
  is_demo         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_app_user_email (email)
) ENGINE=InnoDB;

CREATE TABLE user_preference (
  user_id                    BIGINT PRIMARY KEY,
  timezone                   VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
  day_start_minute           SMALLINT     NOT NULL DEFAULT 360,
  day_end_minute             SMALLINT     NOT NULL DEFAULT 1380,
  core_start_minute          SMALLINT     NOT NULL DEFAULT 480,
  core_end_minute            SMALLINT     NOT NULL DEFAULT 1320,
  min_gap_minutes            SMALLINT     NOT NULL DEFAULT 10,
  heavy_day_threshold_minutes SMALLINT    NOT NULL DEFAULT 240,
  heavy_day_threshold_items  TINYINT      NOT NULL DEFAULT 6,
  prayer_tracking_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
  period_tracking_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
  period_prayer_link_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
  theme                      VARCHAR(16)  NOT NULL DEFAULT 'system',
  CONSTRAINT fk_pref_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Location is optional and only used where it is genuinely needed
-- (prayer times). Absence of a row must not break anything.
CREATE TABLE location_preference (
  user_id            BIGINT PRIMARY KEY,
  label              VARCHAR(120),
  latitude           DECIMAL(9,6),
  longitude          DECIMAL(9,6),
  timezone           VARCHAR(64),
  source             ENUM('MANUAL','BROWSER') NOT NULL DEFAULT 'MANUAL',
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                       ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_loc_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Goals -> Milestones -> Habits/Tasks
-- ---------------------------------------------------------------------

CREATE TABLE goal (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  title         VARCHAR(200) NOT NULL,
  description   TEXT,
  category      VARCHAR(60),
  target_year   SMALLINT,
  target_date   DATE,
  target_count  INT NOT NULL DEFAULT 1,   -- e.g. "travel abroad twice"
  achieved_count INT NOT NULL DEFAULT 0,
  status        ENUM('ACTIVE','PAUSED','ACHIEVED','ABANDONED')
                  NOT NULL DEFAULT 'ACTIVE',
  is_demo       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_goal_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  KEY ix_goal_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE goal_milestone (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  goal_id       BIGINT NOT NULL,
  title         VARCHAR(200) NOT NULL,
  due_date      DATE,
  sort_order    INT NOT NULL DEFAULT 0,
  completed_at  DATETIME NULL,
  CONSTRAINT fk_milestone_goal FOREIGN KEY (goal_id)
    REFERENCES goal (id) ON DELETE CASCADE,
  KEY ix_milestone_goal (goal_id, sort_order)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Habits
-- ---------------------------------------------------------------------

CREATE TABLE habit (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  goal_id           BIGINT NULL,
  milestone_id      BIGINT NULL,
  name              VARCHAR(160) NOT NULL,
  description       TEXT,
  category          VARCHAR(60),
  tracking_type     ENUM('CHECKBOX','NUMERIC','DURATION','COUNT','YES_NO')
                      NOT NULL DEFAULT 'CHECKBOX',
  target_value      DECIMAL(10,2) NULL,     -- 2000 (ml), 60 (minutes), 2 (problems)
  target_unit       VARCHAR(24)   NULL,
  duration_minutes  SMALLINT NOT NULL DEFAULT 30,
  priority          ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
  flexibility       ENUM('FIXED','PREFERRED','FLEXIBLE') NOT NULL DEFAULT 'FLEXIBLE',
  status            ENUM('ACTIVE','PAUSED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  start_date        DATE NOT NULL,
  end_date          DATE NULL,
  is_demo           BOOLEAN NOT NULL DEFAULT FALSE,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_habit_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_habit_goal FOREIGN KEY (goal_id)
    REFERENCES goal (id) ON DELETE SET NULL,
  CONSTRAINT fk_habit_milestone FOREIGN KEY (milestone_id)
    REFERENCES goal_milestone (id) ON DELETE SET NULL,
  KEY ix_habit_user_status (user_id, status)
) ENGINE=InnoDB;

-- The recurrence RULE. One row per habit; occurrences are derived.
CREATE TABLE habit_schedule (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  habit_id            BIGINT NOT NULL,
  frequency_type      ENUM('DAILY','WEEKLY','MONTHLY','YEARLY','INTERVAL','CUSTOM')
                        NOT NULL,
  times_per_period    SMALLINT NOT NULL DEFAULT 1,  -- "2x per week"
  interval_days       SMALLINT NULL,                -- "every 3 days"
  -- Bitmask, Monday = bit 0 .. Sunday = bit 6. NULL means "any day".
  days_of_week_mask   TINYINT UNSIGNED NULL,
  day_of_month        TINYINT NULL,                 -- "every 15th"
  preferred_time_of_day ENUM('ANY','MORNING','AFTERNOON','EVENING','NIGHT')
                        NOT NULL DEFAULT 'ANY',
  preferred_start_minute SMALLINT NULL,
  earliest_minute     SMALLINT NULL,
  latest_minute       SMALLINT NULL,
  CONSTRAINT fk_hsched_habit FOREIGN KEY (habit_id)
    REFERENCES habit (id) ON DELETE CASCADE,
  UNIQUE KEY uq_hsched_habit (habit_id)
) ENGINE=InnoDB;

CREATE TABLE habit_constraint (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  habit_id        BIGINT NOT NULL,
  constraint_type ENUM('MIN_SPACING_DAYS','MAX_SPACING_DAYS','MAX_PER_DAY',
                       'EXCLUDED_DAYS','DEADLINE','CONSECUTIVE_LIMIT') NOT NULL,
  int_value       INT NULL,
  date_value      DATE NULL,
  mask_value      TINYINT UNSIGNED NULL,
  CONSTRAINT fk_hconstraint_habit FOREIGN KEY (habit_id)
    REFERENCES habit (id) ON DELETE CASCADE,
  UNIQUE KEY uq_hconstraint (habit_id, constraint_type)
) ENGINE=InnoDB;

-- Exceptions only: one row per actual completion.
CREATE TABLE habit_completion (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  habit_id         BIGINT NOT NULL,
  user_id          BIGINT NOT NULL,
  occurrence_date  DATE NOT NULL,
  status           ENUM('COMPLETED','SKIPPED','MISSED') NOT NULL DEFAULT 'COMPLETED',
  recorded_value   DECIMAL(10,2) NULL,   -- 1400 ml, 42 minutes, 1 problem
  note             VARCHAR(500),
  completed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_hcomp_habit FOREIGN KEY (habit_id)
    REFERENCES habit (id) ON DELETE CASCADE,
  CONSTRAINT fk_hcomp_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  UNIQUE KEY uq_hcomp (habit_id, occurrence_date),
  KEY ix_hcomp_user_date (user_id, occurrence_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Tasks and calendar events
-- ---------------------------------------------------------------------

CREATE TABLE task (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  goal_id          BIGINT NULL,
  milestone_id     BIGINT NULL,
  habit_id         BIGINT NULL,
  title            VARCHAR(200) NOT NULL,
  description      TEXT,
  category         VARCHAR(60),
  scheduled_date   DATE NULL,
  start_minute     SMALLINT NULL,
  duration_minutes SMALLINT NOT NULL DEFAULT 30,
  due_date         DATE NULL,
  priority         ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
  flexibility      ENUM('FIXED','PREFERRED','FLEXIBLE') NOT NULL DEFAULT 'FLEXIBLE',
  status           ENUM('PENDING','COMPLETED','SKIPPED','RESCHEDULED')
                     NOT NULL DEFAULT 'PENDING',
  completed_at     DATETIME NULL,
  is_demo          BOOLEAN NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_task_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_task_goal FOREIGN KEY (goal_id)
    REFERENCES goal (id) ON DELETE SET NULL,
  CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id)
    REFERENCES goal_milestone (id) ON DELETE SET NULL,
  CONSTRAINT fk_task_habit FOREIGN KEY (habit_id)
    REFERENCES habit (id) ON DELETE SET NULL,
  KEY ix_task_user_date (user_id, scheduled_date, status)
) ENGINE=InnoDB;

-- Ordered prerequisites: "book flight" cannot precede "visa research".
CREATE TABLE task_dependency (
  task_id        BIGINT NOT NULL,
  depends_on_id  BIGINT NOT NULL,
  PRIMARY KEY (task_id, depends_on_id),
  CONSTRAINT fk_dep_task FOREIGN KEY (task_id)
    REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT fk_dep_prereq FOREIGN KEY (depends_on_id)
    REFERENCES task (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE calendar_event (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  title            VARCHAR(200) NOT NULL,
  location         VARCHAR(200),
  event_date       DATE NOT NULL,
  start_minute     SMALLINT NOT NULL,
  end_minute       SMALLINT NOT NULL,
  flexibility      ENUM('FIXED','PREFERRED','FLEXIBLE') NOT NULL DEFAULT 'FIXED',
  -- Weekly repeat rule for things like college. NULL = one-off.
  repeat_until     DATE NULL,
  days_of_week_mask TINYINT UNSIGNED NULL,
  external_source  VARCHAR(40) NULL,   -- reserved for future calendar sync
  is_demo          BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_event_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT ck_event_time CHECK (end_minute > start_minute),
  KEY ix_event_user_date (user_id, event_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- User-declared relationships between activities
-- ---------------------------------------------------------------------

CREATE TABLE activity_relationship (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  source_type     ENUM('HABIT','TASK','GOAL','EVENT') NOT NULL,
  source_id       BIGINT NOT NULL,
  target_type     ENUM('HABIT','TASK','GOAL','EVENT') NOT NULL,
  target_id       BIGINT NOT NULL,
  relation_type   ENUM('NONE','AVOID_SAME_DAY','AVOID_CONSECUTIVE_DAYS',
                       'MIN_DAYS_APART','SCHEDULE_BEFORE','SCHEDULE_AFTER',
                       'CAN_OVERLAP') NOT NULL,
  days_value      SMALLINT NOT NULL DEFAULT 0,
  note            VARCHAR(255),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rel_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  UNIQUE KEY uq_rel (user_id, source_type, source_id, target_type, target_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Proposals (never the live schedule) and the approved schedule
-- ---------------------------------------------------------------------

CREATE TABLE schedule_proposal (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  origin           ENUM('NEW_HABIT','NEW_GOAL','OPTIMIZE','REBALANCE',
                        'WHAT_IF','NATURAL_LANGUAGE') NOT NULL,
  strategy         VARCHAR(32) NOT NULL DEFAULT 'SPREAD',
  horizon_start    DATE NOT NULL,
  horizon_days     SMALLINT NOT NULL,
  score            DECIMAL(8,3),
  status           ENUM('PENDING','APPROVED','REJECTED','SUPERSEDED')
                     NOT NULL DEFAULT 'PENDING',
  explanation_json JSON,         -- ordered list of explanation lines
  -- The habit/goal definition this proposal is for, held here rather than
  -- in `habit` so that nothing exists in the real tables before approval.
  draft_json       JSON,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at       DATETIME NULL,
  CONSTRAINT fk_proposal_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  KEY ix_proposal_user_status (user_id, status, created_at)
) ENGINE=InnoDB;

CREATE TABLE schedule_proposal_item (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  proposal_id      BIGINT NOT NULL,
  source_type      ENUM('HABIT','TASK','EVENT') NOT NULL,
  source_id        BIGINT NULL,          -- NULL while the habit is unsaved
  display_name     VARCHAR(160) NOT NULL,
  scheduled_date   DATE NOT NULL,
  start_minute     SMALLINT NOT NULL,
  end_minute       SMALLINT NOT NULL,
  reason_codes     JSON,                 -- structured, drives the explanation
  CONSTRAINT fk_pitem_proposal FOREIGN KEY (proposal_id)
    REFERENCES schedule_proposal (id) ON DELETE CASCADE,
  KEY ix_pitem_proposal_date (proposal_id, scheduled_date)
) ENGINE=InnoDB;

CREATE TABLE schedule_conflict (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  proposal_id      BIGINT NOT NULL,
  conflict_type    VARCHAR(40) NOT NULL,
  severity         ENUM('HARD','SOFT','INFO') NOT NULL,
  message          VARCHAR(600) NOT NULL,
  conflict_date    DATE NULL,
  details_json     JSON,
  CONSTRAINT fk_conflict_proposal FOREIGN KEY (proposal_id)
    REFERENCES schedule_proposal (id) ON DELETE CASCADE,
  KEY ix_conflict_proposal (proposal_id, severity)
) ENGINE=InnoDB;

-- The live plan. Rows arrive here only via proposal approval.
CREATE TABLE scheduled_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  source_type       ENUM('HABIT','TASK','EVENT') NOT NULL,
  source_id         BIGINT NOT NULL,
  display_name      VARCHAR(160) NOT NULL,
  scheduled_date    DATE NOT NULL,
  start_minute      SMALLINT NOT NULL,
  end_minute        SMALLINT NOT NULL,
  approved_from_proposal BIGINT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sitem_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_sitem_proposal FOREIGN KEY (approved_from_proposal)
    REFERENCES schedule_proposal (id) ON DELETE SET NULL,
  CONSTRAINT ck_sitem_time CHECK (end_minute > start_minute),
  KEY ix_sitem_user_date (user_id, scheduled_date, start_minute)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Prayer (times are always computed, never stored as fixed values)
-- ---------------------------------------------------------------------

CREATE TABLE prayer_setting (
  user_id             BIGINT PRIMARY KEY,
  calculation_method  VARCHAR(40) NOT NULL DEFAULT 'KARACHI',
  asr_method          ENUM('STANDARD','HANAFI') NOT NULL DEFAULT 'STANDARD',
  high_latitude_rule  VARCHAR(40) NULL,
  reminders_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
  reminder_lead_minutes SMALLINT NOT NULL DEFAULT 10,
  CONSTRAINT fk_prayerset_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE prayer_completion (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  prayer_date   DATE NOT NULL,
  prayer_name   ENUM('FAJR','DHUHR','ASR','MAGHRIB','ISHA') NOT NULL,
  status        ENUM('COMPLETED','MISSED','EXEMPT') NOT NULL DEFAULT 'COMPLETED',
  completed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prayercomp_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  UNIQUE KEY uq_prayercomp (user_id, prayer_date, prayer_name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Period tracking (private; only read by features the user enabled)
-- ---------------------------------------------------------------------

CREATE TABLE period_entry (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  start_date    DATE NOT NULL,
  end_date      DATE NULL,
  note          VARCHAR(500),
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_period_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  KEY ix_period_user_date (user_id, start_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Reminders (always optional)
-- ---------------------------------------------------------------------

CREATE TABLE reminder (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT NOT NULL,
  source_type    ENUM('HABIT','TASK','PRAYER','GOAL') NOT NULL,
  source_id      BIGINT NULL,
  remind_minute  SMALLINT NULL,          -- NULL for prayer: derived from times
  lead_minutes   SMALLINT NOT NULL DEFAULT 0,
  channel        ENUM('IN_APP','EMAIL','PUSH') NOT NULL DEFAULT 'IN_APP',
  enabled        BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  KEY ix_reminder_user (user_id, enabled)
) ENGINE=InnoDB;
