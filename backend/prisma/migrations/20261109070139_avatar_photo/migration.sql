-- AlterTable
ALTER TABLE "UserProfile" ADD COLUMN     "avatarPhotoAt" TIMESTAMP(3);

-- CreateTable
CREATE TABLE "AvatarPhoto" (
    "userId" UUID NOT NULL,
    "contentType" TEXT NOT NULL,
    "data" BYTEA NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AvatarPhoto_pkey" PRIMARY KEY ("userId")
);

-- AddForeignKey
ALTER TABLE "AvatarPhoto" ADD CONSTRAINT "AvatarPhoto_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
