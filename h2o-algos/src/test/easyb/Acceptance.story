tags "sample test story"

description
	"""
	Test that the template picks up an easyb story
	"""
scenario "test proof of concept", {
	when "given a story", {
		exists = true
        fake = false
	}
	then "story exists", {
        exists.shouldBe true
	}
	then "is not a fake", {
        fake.shouldBe false
	}
}

