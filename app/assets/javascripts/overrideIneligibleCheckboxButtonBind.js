/*
    For the ineligible page, the call handler can override the ineligibility response, but to
    do so, they must check a box to confirm there is evidence to support their eligibility

    This binds the enabled/disabled status of the confirmation button with the confirmation
    checkbox
*/

;(function bindIneligibleContinueButtonWithCheckbox() {
  const checkbox = document.querySelector('[name="confirm_eligible"]')
  const button = document.querySelector('#continue')

  if (checkbox && button) {
    const enableButton = function() {
      button.removeAttribute('aria-disabled')
      button.removeAttribute('disabled')
    }
    const disableButton = function() {
      button.setAttribute('aria-disabled', 'true')
      button.setAttribute('disabled', 'disabled')
    }

    checkbox.addEventListener('change', function(e) {
      const isChecked = e.currentTarget.checked;
      if (isChecked) {
        enableButton()
      } else {
        disableButton()
      }
    })

    if (checkbox.checked) {
      enableButton()
    }
  }
})();
