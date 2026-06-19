package testutils

import core.AppString
import core.StringProviderInterface

class FakeStringProvider : StringProviderInterface {
    override fun getString(key: AppString): String = key.name
}
