// Keep /login as a Pages Router alias of the canonical login form at /.
// The E2E flow and existing links can use /login without being redirected
// back to /, which otherwise causes a 10-second navigation wait.
export { default } from './index';
