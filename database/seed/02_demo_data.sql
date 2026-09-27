-- Demo data. Every row is flagged is_demo = TRUE so it can be cleared with
-- a single predicate and is never mistaken for the user's real plan.
--
-- Password below is the BCrypt hash of "demopassword123".
-- Replace it before using this anywhere real.

USE life_planner;

INSERT INTO app_user (email, display_name, password_hash, is_demo) VALUES
  ('demo@lifeplanner.local', 'Demo User',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE);

SET @uid = LAST_INSERT_ID();

INSERT INTO user_preference (user_id, timezone) VALUES (@uid, 'Asia/Kolkata');

-- Fixed commitments: college, Monday to Friday, 9am to 4pm.
INSERT INTO calendar_event
  (user_id, title, event_date, start_minute, end_minute, flexibility, is_demo)
VALUES
  (@uid, 'College', CURDATE(),                      540, 960, 'FIXED', TRUE),
  (@uid, 'College', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 540, 960, 'FIXED', TRUE),
  (@uid, 'College', DATE_ADD(CURDATE(), INTERVAL 2 DAY), 540, 960, 'FIXED', TRUE),
  (@uid, 'College', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 540, 960, 'FIXED', TRUE),
  (@uid, 'College', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 540, 960, 'FIXED', TRUE);

-- Goals
INSERT INTO goal (user_id, title, category, target_year, target_count, is_demo) VALUES
  (@uid, 'Travel abroad twice', 'Travel', 2027, 2, TRUE),
  (@uid, 'Finish the backend certification', 'Career', 2026, 1, TRUE);

SET @travel = (SELECT id FROM goal WHERE user_id = @uid AND title = 'Travel abroad twice');

INSERT INTO goal_milestone (goal_id, title, sort_order) VALUES
  (@travel, 'Research destinations', 1),
  (@travel, 'Choose destinations', 2),
  (@travel, 'Plan the budget', 3),
  (@travel, 'Check passport validity', 4),
  (@travel, 'Research visas', 5),
  (@travel, 'Book flights', 6),
  (@travel, 'Book accommodation', 7);

-- Habits, with their recurrence rules
INSERT INTO habit
  (user_id, name, category, tracking_type, target_value, target_unit,
   duration_minutes, priority, flexibility, start_date, is_demo)
VALUES
  (@uid, 'Learn Korean',  'Learning', 'DURATION', 30,   'minutes', 30, 'HIGH',   'PREFERRED', CURDATE(), TRUE),
  (@uid, 'Solve LeetCode','Learning', 'COUNT',     1,   'problems',45, 'HIGH',   'FLEXIBLE',  CURDATE(), TRUE),
  (@uid, 'Exercise',      'Health',   'DURATION', 45,   'minutes', 45, 'HIGH',   'PREFERRED', CURDATE(), TRUE),
  (@uid, 'Drink water',   'Health',   'NUMERIC',  2000, 'ml',       5, 'MEDIUM', 'FLEXIBLE',  CURDATE(), TRUE),
  (@uid, 'Read',          'Learning', 'NUMERIC',  20,   'pages',   20, 'MEDIUM', 'FLEXIBLE',  CURDATE(), TRUE),
  (@uid, 'Try a new restaurant', 'Social', 'CHECKBOX', NULL, NULL, 90, 'LOW',   'FLEXIBLE',  CURDATE(), TRUE);

-- Recurrence rules. Day masks: Monday = bit 0 ... Sunday = bit 6.
-- 31 = Mon-Fri, 63 = Mon-Sat, 127 = every day.
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask,
   preferred_time_of_day, preferred_start_minute)
SELECT id, 'WEEKLY', 6, 63, 'EVENING', 1140 FROM habit
  WHERE user_id = @uid AND name = 'Learn Korean';
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask, preferred_time_of_day)
SELECT id, 'WEEKLY', 5, NULL, 'EVENING' FROM habit
  WHERE user_id = @uid AND name = 'Solve LeetCode';
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask, preferred_time_of_day)
SELECT id, 'WEEKLY', 4, NULL, 'MORNING' FROM habit
  WHERE user_id = @uid AND name = 'Exercise';
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask, preferred_time_of_day)
SELECT id, 'DAILY', 7, 127, 'ANY' FROM habit
  WHERE user_id = @uid AND name = 'Drink water';
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask, preferred_time_of_day)
SELECT id, 'DAILY', 7, 127, 'NIGHT' FROM habit
  WHERE user_id = @uid AND name = 'Read';
INSERT INTO habit_schedule
  (habit_id, frequency_type, times_per_period, days_of_week_mask, preferred_time_of_day)
SELECT id, 'WEEKLY', 3, NULL, 'EVENING' FROM habit
  WHERE user_id = @uid AND name = 'Try a new restaurant';

-- Exercise sessions should have a clear day between them.
INSERT INTO habit_constraint (habit_id, constraint_type, int_value)
SELECT id, 'MIN_SPACING_DAYS', 1 FROM habit
  WHERE user_id = @uid AND name = 'Exercise';

-- Goal-linked preparation tasks
INSERT INTO task (user_id, goal_id, title, priority, is_demo) VALUES
  (@uid, @travel, 'Compare flight prices',        'MEDIUM', TRUE),
  (@uid, @travel, 'Shortlist accommodation',      'MEDIUM', TRUE),
  (@uid, @travel, 'Check visa requirements',      'HIGH',   TRUE);
