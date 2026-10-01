from wid.fetch import Article, better_image, guardian_large_image, is_guardian_thumb, upgrade_images

# Endereços copiados do feed publicado em 01/10/2026.
BBC = "https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/4257/live/b16f52c0-bdb1-11f1-a64c-550be9e3c66b.jpg"
NPR = ("https://npr.brightspotcdn.com/dims3/default/strip/false/crop/5593x3729+0+0/resize/5593x3729!/?url="
       "http%3A%2F%2Fnpr-brightspot.s3.amazonaws.com%2F75%2Fe3%2F1d681a5d41479bd46578b4568c38%2Fap26274601303241.jpg")
GUARDIAN = ("https://i.guim.co.uk/img/media/c6ff79a8245321a9daf97ec75ea50f92927aea25/940_555_4006_3204/master/4006.jpg"
            "?width=140&quality=85&auto=format&fit=max&s=e2f02fd6f73c326029040d1ceca3e3c1")
MEDIA = "https://i.guim.co.uk/img/media/c6ff79a8245321a9daf97ec75ea50f92927aea25/940_555_4006_3204/master/4006.jpg"
PAGE = f"""
<meta property="og:image" content="{MEDIA}?width=1200&amp;height=630&amp;overlay-base64=L2lt&amp;s=aaa">
<source srcset="{MEDIA}?width=620&amp;quality=85&amp;auto=format&amp;fit=max&amp;s=b1 620w,
 {MEDIA}?width=1225&amp;quality=85&amp;auto=format&amp;fit=max&amp;s=b2 1225w,
 {MEDIA}?width=1920&amp;quality=85&amp;auto=format&amp;fit=max&amp;s=b3 1920w">
<img src="https://i.guim.co.uk/img/media/outra/1.jpg?width=1000&amp;fit=max&amp;s=x">
"""


def test_better_image_bbc_and_npr():
    assert better_image(BBC) == BBC.replace("/standard/240/", "/standard/976/")
    assert "/resize/1200/?url=" in better_image(NPR) and "5593x3729!" not in better_image(NPR).split("crop/")[1][10:]
    assert better_image(GUARDIAN) == GUARDIAN  # assinado: mudar o width dá 401
    assert better_image("https://ynet-pic1.yit.co.il/x.jpg") == "https://ynet-pic1.yit.co.il/x.jpg"
    assert better_image(None) is None


def test_guardian_large_image_from_page():
    assert is_guardian_thumb(GUARDIAN)
    large = guardian_large_image(PAGE, GUARDIAN)
    # a maior entre 600 e 1300, do mesmo id de mídia, sem o selo (overlay) do og:image
    assert large == f"{MEDIA}?width=1225&quality=85&auto=format&fit=max&s=b2"
    assert not is_guardian_thumb(large)
    assert guardian_large_image("<html></html>", GUARDIAN) is None


def test_upgrade_images_limit_and_errors():
    arts = [Article(id=str(i), title="t", summary="", url=f"https://g/{i}", source="The Guardian", lang="en", weight=1.0,
                    published=None, image=GUARDIAN) for i in range(5)]
    calls = []

    def get_html(url):
        calls.append(url)
        if url.endswith("/1"):
            raise RuntimeError("403")
        return PAGE

    assert upgrade_images(arts, get_html, limit=3) == 3
    assert len(calls) == 3
    assert arts[0].image.endswith("s=b2") and arts[1].image == GUARDIAN and arts[3].image == GUARDIAN


def test_better_image_other_sources():
    est = ("https://www.estadao.com.br/resizer/v2/E74R3PRBXFGNNEKFLIFJIKKLWE.jpg?auth=ab5d&amp;smart=true&amp;width=1000")
    assert better_image(est) == est.replace("&amp;", "&")  # com &amp; o servidor dava 400
    cnn = "https://admin.cnnbrasil.com.br/wp-content/uploads/sites/12/2026/09/Avisoes-da-Flydubai-em-aeroporto.jpg?w=200"
    assert better_image(cnn).endswith("?w=976")
    ynet = "https://ynet-pic1.yit.co.il/picserver6/crop_images/2026/08/24/Hkef41RtwGl/Hkef41RtwGl_0_0_1600_1066_0_medium.jpg"
    assert better_image(ynet).endswith("_0_x-large.jpg")
    assert better_image("https://images.wcdn.co.il/f_auto,q_auto,w_300/4/1/0/0/4100657-46.jpg") is None  # 403
    assert better_image("https://media.npr.org/include/images/tracking/npr-rss-pixel.png?story=nx-s1-5986229") is None
    assert better_image("undefined") is None


def test_page_image_any_attribute_order():
    from wid.fetch import page_image
    dw = '<meta data-rh="true" content="image/jpeg" property="og:image:type"/>' \
         '<meta data-rh="true" content="https://static.dw.com/image/75468402_6.jpg" property="og:image"/>'
    assert page_image(dw) == "https://static.dw.com/image/75468402_6.jpg"
    assert page_image('<meta name="twitter:image" content="https://x/y.jpg?a=1&amp;b=2">') == "https://x/y.jpg?a=1&b=2"
    assert page_image("<html></html>") is None


def test_needs_page_image():
    from wid.fetch import needs_page_image

    def art(url, image):
        return Article(id="1", title="t", summary="", url=url, source="s", lang="en", weight=1.0, published=None, image=image)

    assert needs_page_image(art("https://www.aljazeera.com/news/x", None))
    assert not needs_page_image(art("https://news.google.com/rss/articles/abc", None))  # a "foto" é o logo do Google
    assert needs_page_image(art("https://www.defensenews.com/x", "https://cloudfront-us-east-1.images.arcpublishing.com/archetype/Q.jpg"))
    assert not needs_page_image(art("https://www.france24.com/x", "https://s.france24.com/media/display/a/w:1024/p:16x9/x.jpg"))
