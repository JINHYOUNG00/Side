import { describe, it, expect } from 'vitest'
import { authorizeUrl, redirectUri, ACTIVE_PROVIDERS } from '../oauth'

const ORIGIN = 'https://app.example.com'

describe('oauth authorize URL', () => {
  it('활성 공급자는 kakao·google·naver(AUTH-02 활성화)', () => {
    expect([...ACTIVE_PROVIDERS]).toEqual(['kakao', 'google', 'naver'])
  })

  it('redirectUri는 콜백 라우트 경로와 일치한다', () => {
    expect(redirectUri('kakao', ORIGIN)).toBe('https://app.example.com/login/callback/kakao')
  })

  it('카카오 authorize URL은 authorize 엔드포인트와 필수 파라미터를 담는다', () => {
    const url = new URL(authorizeUrl('kakao', ORIGIN))
    expect(url.origin + url.pathname).toBe('https://kauth.kakao.com/oauth/authorize')
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('redirect_uri')).toBe(redirectUri('kakao', ORIGIN))
    expect(url.searchParams.has('client_id')).toBe(true)
  })

  it('구글 authorize URL은 openid scope를 포함한다', () => {
    const url = new URL(authorizeUrl('google', ORIGIN))
    expect(url.origin + url.pathname).toBe('https://accounts.google.com/o/oauth2/v2/auth')
    expect(url.searchParams.get('scope')).toBe('openid email profile')
  })

  it('네이버 authorize URL은 네이버 엔드포인트와 CSRF state를 담는다', () => {
    const url = new URL(authorizeUrl('naver', ORIGIN))
    expect(url.origin + url.pathname).toBe('https://nid.naver.com/oauth2.0/authorize')
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('redirect_uri')).toBe(redirectUri('naver', ORIGIN))
    expect(url.searchParams.get('state')).toBeTruthy()
  })
})
