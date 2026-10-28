/** Two projects: fast unit tests (no DB) and e2e tests against a real PostgreSQL database. */
const base = {
  moduleFileExtensions: ['js', 'json', 'ts'],
  transform: { '^.+\\.ts$': ['ts-jest', { isolatedModules: true }] },
  testEnvironment: 'node',
};
module.exports = {
  projects: [
    { ...base, displayName: 'unit', rootDir: 'src', testRegex: '.*\\.spec\\.ts$' },
    { ...base, displayName: 'e2e', rootDir: 'test', testRegex: '.*\\.e2e-spec\\.ts$', globalSetup: '<rootDir>/global-setup.ts', setupFiles: ['<rootDir>/env.ts'] },
  ],
};
