-- AlterTable
ALTER TABLE "UserPrivacySettings" ALTER COLUMN "explorationEnabled" SET DEFAULT true;

-- Playing is the point of the app: exploration (private tiles, quests, landmarks) starts on for existing accounts too.
-- People can switch it off in Privacy.
UPDATE "UserPrivacySettings" SET "explorationEnabled" = true WHERE "explorationEnabled" = false;
