-- AlterTable
ALTER TABLE "UserPrivacySettings" ALTER COLUMN "historyEnabled" SET DEFAULT true;

-- Places and trips are now part of belonging to a circle (the create and join screens say so), so existing
-- accounts start keeping history too. People can still turn it off in Privacy; their retention choice is kept.
UPDATE "UserPrivacySettings" SET "historyEnabled" = true WHERE "historyEnabled" = false;
