import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login', name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/register', name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      component: () => import('@/views/DashboardLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'workspace' } },
        { path: 'project/:id?', name: 'workspace', component: () => import('@/views/WorkspaceView.vue') },
        { path: 'project/:projectId/doc/:docId', name: 'editor', component: () => import('@/views/WorkspaceView.vue') },
        {
          path: 'project/:id/framework',
          component: () => import('@/views/FrameworkView.vue'),
          meta: { requiresAuth: true },
          children: [
            { path: '', name: 'framework', component: () => import('@/views/FrameworkOverview.vue') },
            { path: 'elements', name: 'framework-elements', component: () => import('@/views/ElementsView.vue') },
            { path: 'write', name: 'framework-write', component: () => import('@/views/WriteView.vue') },
            { path: 'conflicts', name: 'framework-conflicts', component: () => import('@/views/ConflictsView.vue') },
            { path: 'history', name: 'framework-history', component: () => import('@/views/HistoryView.vue') },
            { path: 'tutorial', name: 'framework-tutorial', component: () => import('@/views/TutorialView.vue') },
          ],
        },
      ],
    },
    {
      path: '/styles', name: 'styles',
      component: () => import('@/views/StyleManageView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/settings/ai', name: 'ai-settings',
      component: () => import('@/views/AiSettingsView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

let authReady = false

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore()
  if (!authReady) {
    await auth.init()
    authReady = true
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) next('/login')
  else if (to.meta.guest && auth.isLoggedIn) next('/')
  else next()
})

export default router
