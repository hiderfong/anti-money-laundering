-- Align the seeded local-development admin account hash.
-- Guard by the old seed hash so manually changed admin passwords are not overwritten.
UPDATE `t_user`
SET `password_hash` = '$2a$10$c4ISGZ.nKFX0iC34wYd.8.OdmgqOLJXsrmyMocQY67X4j9gjoFojq'
WHERE `username` = 'admin'
  AND `password_hash` = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi';
