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
        { path: 'styles', name: 'styles', component: () => import('@/views/StyleManageView.vue') },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) next('/login')
  else if (to.meta.guest && auth.isLoggedIn) next('/')
  else next()
})

export default router
