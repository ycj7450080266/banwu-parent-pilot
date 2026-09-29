const DEFAULT_PROFILE = {
  childName: '孩子',
  groupName: '班级群'
}

// Clearly labeled sample entries let the preview show the main interactions
// before a real AppID and cloud task feed are available.
const PREVIEW_TASKS = [
  {
    id: 'preview-english', title: '完成 09 月 24 日英语练习', due: '9月27日 23:59 前',
    source: '示例消息', excerpt: '各位家长好，请提醒孩子完成英语练习，截止时间：2026-09-27 23:59。',
    createdAt: Date.now(), done: false
  },
  {
    id: 'preview-water', title: '明天带水杯到学校', due: '明天',
    source: '示例消息', excerpt: '老师提醒：明天上课记得带水杯。',
    createdAt: Date.now() - 60 * 60 * 1000, done: false
  },
  {
    id: 'preview-clothes', title: '准备一套备用校服', due: '本周',
    source: '示例消息', excerpt: '有同学需要备用校服的话，请家长在群里联系老师。',
    createdAt: Date.now() - 2 * 60 * 60 * 1000, done: true, completedAt: Date.now()
  }
]

App({
  globalData: {
    profile: DEFAULT_PROFILE,
    syncMode: 'local-preview'
  },

  onLaunch() {
    const saved = wx.getStorageSync('banwu.profile')
    const profile = Object.assign({}, DEFAULT_PROFILE, saved || {})
    this.globalData.profile = profile
    wx.setStorageSync('banwu.profile', profile)
    if (!wx.getStorageSync('banwu.tasks')) wx.setStorageSync('banwu.tasks', PREVIEW_TASKS)
  }
})

